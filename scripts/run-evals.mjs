#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const evalsDir = path.join(rootDir, 'evals');
const casesDir = path.join(evalsDir, 'cases');
const actualDir = path.join(evalsDir, 'actual');
const reportsDir = path.join(evalsDir, 'reports');

const allowedSeverities = new Set(['LOW', 'MEDIUM', 'HIGH']);
const allowedCategories = new Set(['BUG', 'SECURITY', 'PERFORMANCE', 'MAINTAINABILITY', 'STYLE', 'TEST']);

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function writeJson(filePath, value) {
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function listCaseFiles() {
  return fs.readdirSync(casesDir)
    .filter((file) => file.endsWith('.json'))
    .sort()
    .map((file) => path.join(casesDir, file));
}

function textOf(finding) {
  return [
    finding.title,
    finding.description,
    finding.recommendation,
    finding.filePath,
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
}

function hasKeywords(finding, keywords = []) {
  const text = textOf(finding);
  return keywords.every((keyword) => text.includes(String(keyword).toLowerCase()));
}

function sameLocation(expected, actual) {
  if (expected.filePath !== actual.filePath) {
    return false;
  }
  if (typeof expected.startLine !== 'number' || typeof actual.startLine !== 'number') {
    return true;
  }
  return Math.abs(expected.startLine - actual.startLine) <= 3;
}

function validateFinding(finding) {
  const errors = [];
  if (!allowedSeverities.has(finding.severity)) errors.push('invalid severity');
  if (!allowedCategories.has(finding.category)) errors.push('invalid category');
  if (!finding.filePath || typeof finding.filePath !== 'string') errors.push('missing filePath');
  if (typeof finding.startLine !== 'number') errors.push('missing startLine');
  if (!finding.title || typeof finding.title !== 'string') errors.push('missing title');
  if (!finding.description || typeof finding.description !== 'string') errors.push('missing description');
  if (!finding.recommendation || typeof finding.recommendation !== 'string') errors.push('missing recommendation');
  return errors;
}

function loadActualFindings(testCase) {
  const actualPath = path.join(actualDir, `${testCase.id}.json`);
  if (fs.existsSync(actualPath)) {
    const actual = readJson(actualPath);
    return {
      source: path.relative(rootDir, actualPath),
      gateRejected: Boolean(actual.gateRejected),
      findings: Array.isArray(actual) ? actual : actual.findings ?? [],
    };
  }
  return {
    source: 'baselineFindings',
    gateRejected: Boolean(testCase.gateRejected),
    findings: testCase.baselineFindings ?? [],
  };
}

function evaluateCase(testCase) {
  const actual = loadActualFindings(testCase);
  const expectedFindings = testCase.expectedFindings ?? [];
  const validationResults = actual.findings.map((finding) => ({
    finding,
    errors: validateFinding(finding),
  }));
  const validFindings = validationResults.filter((result) => result.errors.length === 0)
    .map((result) => result.finding);

  const usedActualIndexes = new Set();
  let expectedHits = 0;
  let severityMatches = 0;
  let categoryMatches = 0;

  for (const expected of expectedFindings) {
    const locationAndKeywordMatches = validFindings
      .map((finding, index) => ({ finding, index }))
      .filter(({ index }) => !usedActualIndexes.has(index))
      .filter(({ finding }) => sameLocation(expected, finding))
      .filter(({ finding }) => hasKeywords(finding, expected.keywords));

    const categoryMatch = locationAndKeywordMatches.find(({ finding }) => finding.category === expected.category);
    const severityMatch = locationAndKeywordMatches.find(({ finding }) => finding.severity === expected.severity);
    if (categoryMatch) categoryMatches += 1;
    if (severityMatch) severityMatches += 1;

    const fullMatch = locationAndKeywordMatches.find(({ finding }) =>
      finding.category === expected.category
      && finding.severity === expected.severity
      && hasKeywords(finding, expected.recommendationKeywords)
    );
    if (fullMatch) {
      expectedHits += 1;
      usedActualIndexes.add(fullMatch.index);
    }
  }

  const falsePositiveCount = Math.max(validFindings.length - usedActualIndexes.size, 0);
  const schemaPassRate = actual.findings.length === 0
    ? 1
    : validFindings.length / actual.findings.length;

  return {
    id: testCase.id,
    title: testCase.title,
    source: actual.source,
    expectedCount: expectedFindings.length,
    actualCount: actual.findings.length,
    validFindingCount: validFindings.length,
    schemaPassRate,
    expectedHits,
    severityMatches,
    categoryMatches,
    falsePositiveCount,
    gateRejected: actual.gateRejected,
    validationErrors: validationResults
      .filter((result) => result.errors.length > 0)
      .map((result) => ({
        title: result.finding.title ?? '(untitled)',
        errors: result.errors,
      })),
  };
}

function percentage(value) {
  return `${Math.round(value * 1000) / 10}%`;
}

function summarize(caseResults) {
  const totals = caseResults.reduce((acc, result) => {
    acc.expectedCount += result.expectedCount;
    acc.actualCount += result.actualCount;
    acc.validFindingCount += result.validFindingCount;
    acc.expectedHits += result.expectedHits;
    acc.severityMatches += result.severityMatches;
    acc.categoryMatches += result.categoryMatches;
    acc.falsePositiveCount += result.falsePositiveCount;
    acc.gateRejectionCount += result.gateRejected ? 1 : 0;
    return acc;
  }, {
    expectedCount: 0,
    actualCount: 0,
    validFindingCount: 0,
    expectedHits: 0,
    severityMatches: 0,
    categoryMatches: 0,
    falsePositiveCount: 0,
    gateRejectionCount: 0,
  });

  return {
    caseCount: caseResults.length,
    expectedFindingCount: totals.expectedCount,
    actualFindingCount: totals.actualCount,
    schemaPassRate: totals.actualCount === 0 ? 1 : totals.validFindingCount / totals.actualCount,
    expectedFindingHitRate: totals.expectedCount === 0 ? 1 : totals.expectedHits / totals.expectedCount,
    severityMatchRate: totals.expectedCount === 0 ? 1 : totals.severityMatches / totals.expectedCount,
    categoryMatchRate: totals.expectedCount === 0 ? 1 : totals.categoryMatches / totals.expectedCount,
    falsePositiveCount: totals.falsePositiveCount,
    gateRejectionCount: totals.gateRejectionCount,
    issueCountDelta: totals.actualCount - totals.expectedCount,
  };
}

function renderMarkdown(report) {
  const rows = report.cases.map((result) => [
    result.id,
    result.expectedCount,
    result.actualCount,
    percentage(result.schemaPassRate),
    `${result.expectedHits}/${result.expectedCount}`,
    result.falsePositiveCount,
    result.gateRejected ? 'yes' : 'no',
    result.source,
  ]);

  return [
    '# CodeReviewX Eval Report',
    '',
    `Generated at: ${report.generatedAt}`,
    '',
    '## Summary',
    '',
    `- Cases: ${report.summary.caseCount}`,
    `- Schema pass rate: ${percentage(report.summary.schemaPassRate)}`,
    `- Expected finding hit rate: ${percentage(report.summary.expectedFindingHitRate)}`,
    `- Severity match rate: ${percentage(report.summary.severityMatchRate)}`,
    `- Category match rate: ${percentage(report.summary.categoryMatchRate)}`,
    `- Issue count delta: ${report.summary.issueCountDelta}`,
    `- False positives: ${report.summary.falsePositiveCount}`,
    `- Gate rejections: ${report.summary.gateRejectionCount}`,
    '',
    '## Cases',
    '',
    '| Case | Expected | Actual | Schema | Hits | False Positives | Gate Rejected | Source |',
    '|---|---:|---:|---:|---:|---:|---|---|',
    ...rows.map((row) => `| ${row.join(' | ')} |`),
    '',
  ].join('\n');
}

function main() {
  fs.mkdirSync(reportsDir, { recursive: true });
  const cases = listCaseFiles().map(readJson);
  const caseResults = cases.map(evaluateCase);
  const report = {
    generatedAt: new Date().toISOString(),
    summary: summarize(caseResults),
    cases: caseResults,
  };

  const jsonPath = path.join(reportsDir, 'latest.json');
  const mdPath = path.join(reportsDir, 'latest.md');
  writeJson(jsonPath, report);
  fs.writeFileSync(mdPath, renderMarkdown(report), 'utf8');

  console.log(`Wrote ${path.relative(rootDir, jsonPath)}`);
  console.log(`Wrote ${path.relative(rootDir, mdPath)}`);
  console.log(`Schema pass rate: ${percentage(report.summary.schemaPassRate)}`);
  console.log(`Expected finding hit rate: ${percentage(report.summary.expectedFindingHitRate)}`);

  if (report.summary.schemaPassRate < 1 || report.summary.expectedFindingHitRate < 1) {
    process.exitCode = 1;
  }
}

main();
