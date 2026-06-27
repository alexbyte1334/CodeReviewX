#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const homebrewCaBundle = '/opt/homebrew/etc/ca-certificates/cert.pem';

function run(command, args, options = {}) {
  console.log(`\n$ ${[command, ...args].join(' ')}`);
  const result = spawnSync(command, args, {
    cwd: rootDir,
    stdio: 'inherit',
    shell: false,
    ...options,
  });
  if (result.error) {
    return { status: 1, error: result.error };
  }
  return { status: result.status ?? 0 };
}

function commandExists(command) {
  const result = spawnSync('sh', ['-lc', `command -v ${command}`], {
    cwd: rootDir,
    stdio: 'ignore',
  });
  return result.status === 0;
}

function semgrepEnv() {
  const env = {
    ...process.env,
    SEMGREP_SEND_METRICS: process.env.SEMGREP_SEND_METRICS ?? 'off',
  };
  if (!env.SSL_CERT_FILE && fs.existsSync(homebrewCaBundle)) {
    env.SSL_CERT_FILE = homebrewCaBundle;
  }
  return env;
}

function main() {
  const failures = [];

  for (const [command, args] of [
    ['node', ['scripts/secret-scan.mjs']],
    ['node', ['scripts/dependency-scan.mjs']],
  ]) {
    const result = run(command, args);
    if (result.status !== 0) {
      failures.push(`${command} ${args.join(' ')}`);
    }
  }

  if (commandExists('semgrep')) {
    const result = run('semgrep', [
      'scan',
      '--config',
      '.semgrep.yml',
      '--error',
      '--exclude',
      'frontend/node_modules',
      '--exclude',
      'backend-java/target',
      '--exclude',
      'frontend/dist',
    ], { env: semgrepEnv() });
    if (result.status !== 0) {
      failures.push('semgrep scan --config .semgrep.yml --error');
    }
  } else if (process.env.REQUIRE_SEMGREP === '1') {
    console.error('\nSemgrep is required but was not found on PATH.');
    failures.push('semgrep missing');
  } else {
    console.log('\nSemgrep not found; skipped local Semgrep scan.');
    console.log('Install Semgrep or set REQUIRE_SEMGREP=1 in CI to enforce it.');
  }

  if (failures.length > 0) {
    console.error('\nStatic scan failed:');
    for (const failure of failures) {
      console.error(`- ${failure}`);
    }
    process.exitCode = 1;
    return;
  }

  console.log('\nStatic scan passed.');
}

main();
