#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const reportsDir = path.join(rootDir, 'evals', 'reports');

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function writeJson(filePath, value) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function directNpmDependencies() {
  const packageJson = readJson(path.join(rootDir, 'frontend', 'package.json'));
  const lock = readJson(path.join(rootDir, 'frontend', 'package-lock.json'));
  const requested = {
    ...packageJson.dependencies,
    ...packageJson.devDependencies,
  };

  return Object.entries(requested).map(([name, range]) => {
    const locked = lock.packages?.[`node_modules/${name}`];
    return {
      ecosystem: 'npm',
      name,
      requestedRange: range,
      version: locked?.version ?? null,
      dev: Boolean(packageJson.devDependencies?.[name]),
    };
  });
}

function tagText(xml, tagName) {
  const match = xml.match(new RegExp(`<${tagName}>([\\s\\S]*?)</${tagName}>`));
  return match ? match[1].trim() : null;
}

function directMavenDependencies() {
  const pomText = fs.readFileSync(path.join(rootDir, 'backend-java', 'pom.xml'), 'utf8');
  const dependencyBlocks = [...pomText.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)]
    .map((match) => match[1]);
  return dependencyBlocks.map((dependency) => ({
    ecosystem: 'maven',
    groupId: tagText(dependency, 'groupId'),
    artifactId: tagText(dependency, 'artifactId'),
    version: tagText(dependency, 'version') ?? '(managed)',
    scope: tagText(dependency, 'scope') ?? 'compile',
  }));
}

function findNpmIssues(dependencies) {
  const issues = [];
  for (const dependency of dependencies) {
    if (!dependency.version) {
      issues.push({
        severity: 'HIGH',
        dependency: dependency.name,
        message: 'Dependency is missing from package-lock.json.',
      });
    }
    if (String(dependency.requestedRange).includes('latest') || String(dependency.requestedRange).includes('*')) {
      issues.push({
        severity: 'MEDIUM',
        dependency: dependency.name,
        message: 'Dependency range uses latest or wildcard.',
      });
    }
  }
  return issues;
}

function findMavenIssues(dependencies) {
  const issues = [];
  for (const dependency of dependencies) {
    if (String(dependency.version).includes('SNAPSHOT')) {
      issues.push({
        severity: 'MEDIUM',
        dependency: `${dependency.groupId}:${dependency.artifactId}`,
        message: 'Dependency uses a SNAPSHOT version.',
      });
    }
    if (dependency.groupId === 'com.h2database' && dependency.scope !== 'test') {
      issues.push({
        severity: 'INFO',
        dependency: `${dependency.groupId}:${dependency.artifactId}`,
        message: 'H2 is a local MVP runtime dependency; do not use this setup for production.',
      });
    }
  }
  return issues;
}

function main() {
  const npmDependencies = directNpmDependencies();
  const mavenDependencies = directMavenDependencies();
  const issues = [
    ...findNpmIssues(npmDependencies),
    ...findMavenIssues(mavenDependencies),
  ];
  const blockingIssues = issues.filter((issue) => issue.severity !== 'INFO');
  const report = {
    generatedAt: new Date().toISOString(),
    summary: {
      npmDirectDependencyCount: npmDependencies.length,
      mavenDirectDependencyCount: mavenDependencies.length,
      issueCount: issues.length,
      blockingIssueCount: blockingIssues.length,
    },
    npmDependencies,
    mavenDependencies,
    issues,
  };

  const reportPath = path.join(reportsDir, 'dependency-scan-latest.json');
  writeJson(reportPath, report);

  console.log(`Wrote ${path.relative(rootDir, reportPath)}`);
  console.log(`npm direct dependencies: ${npmDependencies.length}`);
  console.log(`maven direct dependencies: ${mavenDependencies.length}`);
  console.log(`blocking dependency issues: ${blockingIssues.length}`);

  for (const issue of issues) {
    console.log(`[${issue.severity}] ${issue.dependency}: ${issue.message}`);
  }

  if (blockingIssues.length > 0) {
    process.exitCode = 1;
  }
}

main();
