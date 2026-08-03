const { app, BrowserWindow, dialog } = require('electron');
const { spawn, spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');

const PORT = 8080;
let backend;
let postgres;
let backendExit = null;
let postgresExit = null;
const dataRoot = path.join(app.getPath('appData'), 'CodeReviewX');
const runtimeRoot = process.resourcesPath;
const logDir = path.join(dataRoot, 'logs');

function credentialConfig() {
  const result = spawnSync('security', ['find-generic-password', '-s', 'CodeReviewX.credentials', '-w'], { encoding: 'utf8' });
  if (result.status !== 0 || !result.stdout.trim()) return {};
  try { return JSON.parse(result.stdout.trim()); } catch { return {}; }
}

function startProcess(command, args, env, logName) {
  fs.mkdirSync(logDir, { recursive: true });
  const log = fs.openSync(path.join(logDir, logName), 'a');
  const child = spawn(command, args, { env: { ...process.env, ...env }, stdio: ['ignore', log, log] });
  child.on('exit', (code, signal) => {
    if (logName === 'backend.log') backendExit = { code, signal };
    if (logName === 'postgres.log') postgresExit = { code, signal };
  });
  child.on('error', error => {
    if (logName === 'backend.log') backendExit = { error: error.message };
    if (logName === 'postgres.log') postgresExit = { error: error.message };
  });
  return child;
}

function runChecked(command, args, stage) {
  const result = spawnSync(command, args, { encoding: 'utf8' });
  if (result.status !== 0) {
    const detail = (result.stderr || result.stdout || '').trim().replace(/\s+/g, ' ').slice(0, 240);
    throw new Error(`${stage}${detail ? `: ${detail}` : ''}`);
  }
  return result.stdout || '';
}

function startPostgres() {
  const pg = path.join(runtimeRoot, 'postgresql');
  const data = path.join(dataRoot, 'postgres');
  const initdb = path.join(pg, 'bin', 'initdb');
  const pgCtl = path.join(pg, 'bin', 'pg_ctl');
  const createdb = path.join(pg, 'bin', 'createdb');
  const psql = path.join(pg, 'bin', 'psql');
  const postgresBinary = path.join(pg, 'bin', 'postgres');
  const vector = path.join(pg, 'lib', 'postgresql', 'vector.dylib');
  if (![initdb, pgCtl, createdb, psql, postgresBinary, vector].every(fs.existsSync)) {
    throw new Error('POSTGRES_RUNTIME_INVALID: 内置 PostgreSQL/pgvector 运行时缺失，请重新下载完整 DMG。');
  }
  fs.mkdirSync(data, { recursive: true });
  if (!fs.existsSync(path.join(data, 'PG_VERSION'))) {
    const result = spawnSync(initdb, ['-D', data, '--encoding=UTF8', '--locale=C'], { encoding: 'utf8' });
    if (result.status !== 0) throw new Error('POSTGRES_INIT_FAILED: 无法初始化本地数据库。');
  }
  postgresExit = null;
  postgres = startProcess(postgresBinary, ['-D', data, '-p', String(PORT + 1)], { PGDATA: data }, 'postgres.log');
  let ready = false;
  for (let attempt = 0; attempt < 120; attempt += 1) {
    if (postgresExit) throw new Error('POSTGRES_START_FAILED: 本地数据库进程已退出，请查看 logs/postgres.log。');
    const result = spawnSync(psql, ['-p', String(PORT + 1), '-d', 'postgres', '-c', 'SELECT 1'], { encoding: 'utf8' });
    if (result.status === 0) { ready = true; break; }
    spawnSync('sleep', ['0.5']);
  }
  if (!ready) throw new Error('POSTGRES_START_TIMEOUT: 本地数据库启动超时，请查看 logs/postgres.log。');
  runChecked(psql, ['-p', String(PORT + 1), '-d', 'postgres', '-c',
    "DO $$ BEGIN CREATE ROLE codereviewx LOGIN; EXCEPTION WHEN duplicate_object THEN NULL; END $$;"], 'POSTGRES_ROLE_FAILED');
  const databaseExists = spawnSync(psql, ['-p', String(PORT + 1), '-d', 'postgres', '-tAc',
    "SELECT 1 FROM pg_database WHERE datname='codereviewx'"], { encoding: 'utf8' }).stdout.trim() === '1';
  if (!databaseExists) runChecked(createdb, ['-p', String(PORT + 1), '-O', 'codereviewx', 'codereviewx'], 'POSTGRES_DATABASE_FAILED');
  runChecked(psql, ['-p', String(PORT + 1), '-d', 'codereviewx', '-c', 'CREATE EXTENSION IF NOT EXISTS vector'], 'PGVECTOR_INIT_FAILED');
}

function startBackend() {
  const java = path.join(runtimeRoot, 'jre', 'bin', 'java');
  const jar = path.join(runtimeRoot, 'backend', 'app.jar');
  if (!fs.existsSync(java) || !fs.existsSync(jar)) throw new Error('内置 Java 运行时或 Backend 缺失，请重新下载完整 DMG。');
  const config = credentialConfig();
  backendExit = null;
  backend = startProcess(java, ['-XX:MaxRAMPercentage=75', '-jar', jar], {
    SERVER_ADDRESS: '127.0.0.1', BACKEND_PORT: String(PORT), SPRING_PROFILES_ACTIVE: 'postgres',
    POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: String(PORT + 1), POSTGRES_DB: 'codereviewx',
    POSTGRES_USER: 'codereviewx', POSTGRES_PASSWORD: '', RAG_ENABLED: (config.embeddingApiKey && config.rerankApiKey) ? 'true' : 'false', RAG_WORK_ROOT: path.join(dataRoot, 'rag-work'),
    MODEL_PROVIDER: config.provider || 'custom', MODEL_BASE_URL: config.modelBaseUrl || '', MODEL_NAME: config.modelName || '', MODEL_API_KEY: config.modelApiKey || '',
    GITHUB_TOKEN: config.githubToken || '', RAG_EMBEDDING_BASE_URL: config.embeddingBaseUrl || '', RAG_EMBEDDING_API_KEY: config.embeddingApiKey || '', RAG_EMBEDDING_MODEL: config.embeddingModel || 'BAAI/bge-m3',
    RAG_RERANK_BASE_URL: config.rerankBaseUrl || '', RAG_RERANK_API_KEY: config.rerankApiKey || '', RAG_RERANK_MODEL: config.rerankModel || 'BAAI/bge-reranker-v2-m3'
  }, 'backend.log');
}

function waitForBackend() {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const check = () => {
      if (backendExit) return reject(new Error('BACKEND_START_FAILED: Backend 已退出，请查看 logs/backend.log。'));
      const request = http.get(`http://127.0.0.1:${PORT}/actuator/health/liveness`, response => {
        response.resume();
        if (response.statusCode === 200) return resolve();
        if (Date.now() - started > 60000) return reject(new Error('HEALTH_TIMEOUT: Backend 健康检查超时，请查看 logs/backend.log。'));
        setTimeout(check, 500);
      });
      request.on('error', () => {
        if (backendExit) reject(new Error('BACKEND_START_FAILED: Backend 已退出，请查看 logs/backend.log。'));
        else if (Date.now() - started > 60000) reject(new Error('HEALTH_TIMEOUT: Backend 健康检查超时，请查看 logs/backend.log。'));
        else setTimeout(check, 500);
      });
    };
    check();
  });
}

async function createWindow() {
  try {
    for (const directory of ['config', 'data', 'postgres', 'rag-work', 'logs']) fs.mkdirSync(path.join(dataRoot, directory), { recursive: true });
    startPostgres(); startBackend(); await waitForBackend();
  } catch (error) {
    if (backend && !backend.killed) backend.kill('SIGTERM');
    if (postgres && !postgres.killed) postgres.kill('SIGTERM');
    dialog.showErrorBox('CodeReviewX 启动失败', `${error.message}\n\n日志：${logDir}`);
    app.quit(); return;
  }
  const window = new BrowserWindow({ width: 1280, height: 860, webPreferences: { preload: path.join(__dirname, 'preload.cjs'), contextIsolation: true, nodeIntegration: false } });
  await window.loadFile(path.join(runtimeRoot, 'frontend', 'index.html'));
}

app.whenReady().then(createWindow);
app.on('before-quit', () => { if (backend && !backend.killed) backend.kill('SIGTERM'); if (postgres && !postgres.killed) postgres.kill('SIGTERM'); });
