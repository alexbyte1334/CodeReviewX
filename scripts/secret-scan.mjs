#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const excludedDirs = new Set([
  '.git',
  '.cursor',
  '.qoder',
  '.vscode',
  'node_modules',
  'target',
  'dist',
  'coverage',
  'backend-java/data',
  'frontend/node_modules',
  'frontend/dist',
  'stage 1',
  'stage 1.5',
  'stage 2',
]);
const excludedFiles = new Set([
  '.env',
  '.env.local',
  '.env.development',
  '.env.production',
  'docs/mimo_api_key.md',
]);

const placeholderValues = [
  '',
  'change_me',
  'replace_with_your_github_token',
  'replace_with_your_planner_mimo_api_key',
  'replace_with_your_executor_mimo_api_key',
  '<your-planner-key>',
  '<your-executor-key>',
  '<local-planner-secret-not-committed>',
  '<local-executor-secret-not-committed>',
  'test-token',
  'DEMO_SECRET_TOKEN_SHOULD_NOT_SHIP',
];

const tokenPatterns = [
  {
    name: 'github classic token',
    pattern: /\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{20,}\b/g,
  },
  {
    name: 'github fine-grained token',
    pattern: /\bgithub_pat_[A-Za-z0-9_]{20,}\b/g,
  },
  {
    name: 'openai-style api key',
    pattern: /\bsk-[A-Za-z0-9]{20,}\b/g,
  },
  {
    name: 'private key block',
    pattern: /-----BEGIN (?:RSA |EC |OPENSSH |)PRIVATE KEY-----/g,
  },
  {
    name: 'authorization bearer token',
    pattern: /Authorization:\s*Bearer\s+(?!<|replace|token|example|ghp_secret)[A-Za-z0-9._-]{16,}/gi,
  },
];

const sensitiveAssignmentPattern =
  /^\s*(GITHUB_TOKEN|MIMO_[A-Z_]*API_KEY|MYSQL_PASSWORD|OPENAI_API_KEY)\s*=\s*([^#\s]+)\s*$/;

function relativePath(filePath) {
  return path.relative(rootDir, filePath).split(path.sep).join('/');
}

function isExcludedDir(dirPath) {
  const rel = relativePath(dirPath);
  if (rel === '') return false;
  return [...excludedDirs].some((excluded) => rel === excluded || rel.startsWith(`${excluded}/`));
}

function isExcludedFile(filePath) {
  return excludedFiles.has(relativePath(filePath));
}

function walk(dirPath, files = []) {
  if (isExcludedDir(dirPath)) return files;
  for (const entry of fs.readdirSync(dirPath, { withFileTypes: true })) {
    const entryPath = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      walk(entryPath, files);
    } else if (entry.isFile() && !isExcludedFile(entryPath)) {
      files.push(entryPath);
    }
  }
  return files;
}

function isLikelyText(buffer) {
  if (buffer.includes(0)) return false;
  const sample = buffer.subarray(0, Math.min(buffer.length, 4096)).toString('utf8');
  return !sample.includes('\uFFFD');
}

function isPlaceholder(value) {
  return placeholderValues.includes(value)
    || value.startsWith('replace_with_')
    || value.startsWith('<')
    || value.startsWith('${');
}

function scanFile(filePath) {
  const buffer = fs.readFileSync(filePath);
  if (buffer.length > 1024 * 1024 || !isLikelyText(buffer)) {
    return [];
  }

  const text = buffer.toString('utf8');
  const findings = [];
  for (const { name, pattern } of tokenPatterns) {
    pattern.lastIndex = 0;
    for (const match of text.matchAll(pattern)) {
      const line = text.slice(0, match.index).split('\n').length;
      findings.push({ file: relativePath(filePath), line, type: name });
    }
  }

  text.split(/\r?\n/).forEach((lineText, index) => {
    const match = lineText.match(sensitiveAssignmentPattern);
    if (!match) return;
    const value = match[2].trim().replace(/^["']|["']$/g, '');
    if (!isPlaceholder(value) && value.length >= 8) {
      findings.push({
        file: relativePath(filePath),
        line: index + 1,
        type: `sensitive assignment ${match[1]}`,
      });
    }
  });

  return findings;
}

function main() {
  const findings = walk(rootDir).flatMap(scanFile);
  if (findings.length === 0) {
    console.log('Secret scan passed: no high-confidence secrets found.');
    return;
  }

  console.error('Secret scan failed: potential secrets found.');
  for (const finding of findings) {
    console.error(`- ${finding.file}:${finding.line} ${finding.type}`);
  }
  process.exitCode = 1;
}

main();
