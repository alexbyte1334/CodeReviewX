#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const base = path.join(root, 'evals/rag');
const casesDir = path.join(base, 'cases');
const manifest = JSON.parse(fs.readFileSync(path.join(base, 'corpus/manifest.json'), 'utf8'));
const cases = fs.readdirSync(casesDir).filter(f => f.endsWith('.json')).sort().map(f => JSON.parse(fs.readFileSync(path.join(casesDir, f), 'utf8')));
const tokens = s => new Set(String(s).toLowerCase().match(/[\p{L}\p{N}_]+/gu) || []);
const score = (q, text, vector) => { const qt = [...tokens(q)]; const tt = tokens(text); return qt.reduce((n, t, i) => n + (tt.has(t) ? (vector ? 1 / (i + 1) : 1) : 0), 0); };
const corpus = manifest.chunks.map(m => ({ ...m, text: fs.readFileSync(path.join(base, 'corpus/sample-repo', m.key), 'utf8') }));

function evaluate(c, mutation = {}) {
  const target = c.targetCommit;
  const eligible = corpus.filter(x => mutation.crossCommit || x.commit === target).filter(x => mutation.noExclusions || !x.excluded);
  const expandedQuery = `${c.query} ${c.changedPaths.join(' ')}`;
  const routes = ['lexical', 'vector'].map(route => eligible.map(x => ({ ...x, s: score(expandedQuery, `${x.key} ${x.text}`, route === 'vector') }))
    .sort((a, b) => b.s - a.s || a.key.localeCompare(b.key)).slice(0, 40));
  const merged = new Map(); routes.forEach(route => route.forEach((x, i) => { const old = merged.get(x.key); merged.set(x.key, { ...x, rrf: (old?.rrf || 0) + 1 / (60 + i + 1) }); }));
  let ranked = [...merged.values()].sort((a, b) => b.rrf - a.rrf || a.key.localeCompare(b.key)).slice(0, 30);
  if (mutation.injectForbidden) ranked = [...ranked, ...corpus.filter(x => c.forbiddenChunkKeys.includes(x.key))];
  const selected = ranked.filter(x => mutation.crossCommit || mutation.injectForbidden || x.commit === target).filter(x => mutation.noExclusions || !x.excluded).slice(0, mutation.overBudget ? 100 : 12);
  const relevant = x => c.relevantChunkKeys.includes(x.key);
  const first = selected.findIndex(relevant);
  const dcg = a => a.reduce((s, v, i) => s + (v ? 1 / Math.log2(i + 2) : 0), 0);
  const chars = selected.reduce((s, x) => s + x.text.length, 0) + (mutation.overBudget ? 36001 : 0);
  return { id: c.id, recallAt5: selected.slice(0, 5).filter(relevant).length / Math.max(c.relevantChunkKeys.length, 1), recallAt10: selected.slice(0, 10).filter(relevant).length / Math.max(c.relevantChunkKeys.length, 1), mrrAt10: first < 0 || first >= 10 ? 0 : 1 / (first + 1), ndcgAt10: dcg(selected.slice(0, 10).map(relevant)) / Math.max(dcg(c.relevantChunkKeys.map(() => true)), 1), forbiddenHits: selected.filter(x => c.forbiddenChunkKeys.includes(x.key)).length, selectedChunks: selected.length, selectedChars: chars, latencyMs: 0, budgetViolation: selected.length > 12 || chars > 36000, crossCommitContamination: selected.some(x => x.commit !== target), expectedFindingPass: c.expectedFinding?.startsWith('no code') ? !selected.some(relevant) : selected.some(relevant), routeCounts: { lexical: routes[0].length, vector: routes[1].length }, candidateCount: ranked.length, rerankCandidateViolation: ranked.length > 30 };
}

function run(mutation = {}) { const rows = cases.map(c => evaluate(c, mutation)); const positive = rows.filter((_, i) => cases[i].relevantChunkKeys.length); const avg = (k, source = rows) => source.reduce((s, r) => s + r[k], 0) / source.length; return { rows, metrics: { recallAt5: avg('recallAt5', positive), recallAt10: avg('recallAt10', positive), mrrAt10: avg('mrrAt10', positive), ndcgAt10: avg('ndcgAt10', positive), forbiddenHitRate: rows.reduce((s, r) => s + r.forbiddenHits, 0) / rows.length, averageSelectedChunks: avg('selectedChunks'), averageSelectedChars: avg('selectedChars'), p95SelectedChars: [...rows].sort((a, b) => a.selectedChars - b.selectedChars)[Math.min(rows.length - 1, Math.ceil(rows.length * .95) - 1)].selectedChars, p95LatencyMs: 0, contextBudgetViolations: rows.filter(r => r.budgetViolation).length, rerankCandidateViolations: rows.filter(r => r.rerankCandidateViolation).length, crossCommitContamination: rows.filter(r => r.crossCommitContamination).length, expectedFindingPass: rows.filter(r => r.expectedFindingPass).length / rows.length } }; }
const thresholds = { recallAt10: .85, mrrAt10: .7, ndcgAt10: .75, forbiddenHitRate: 0, contextBudgetViolations: 0, crossCommitContamination: 0, expectedFindingPass: 1 };
const fails = m => Object.entries(thresholds).filter(([k, v]) => ['forbiddenHitRate', 'contextBudgetViolations', 'crossCommitContamination'].includes(k) ? m[k] !== v : m[k] < v).map(([k]) => k);
if (process.argv.includes('--self-test')) { for (const mutation of [{ injectForbidden: true }, { crossCommit: true }, { overBudget: true }]) { const f = fails(run(mutation).metrics); if (!f.length) throw new Error(`mutation did not fail: ${JSON.stringify(mutation)}`); } console.log('RAG mutation self-test PASS'); process.exit(0); }
if (process.env.RAG_LIVE_EVAL === '1') { for (const key of ['RAG_EMBEDDING_URL', 'RAG_RERANK_URL', 'RAG_EMBEDDING_API_KEY', 'RAG_RERANK_API_KEY', 'RAG_EMBEDDING_MODEL', 'RAG_RERANK_MODEL']) if (!process.env[key]) { console.error(`RAG_LIVE_EVAL missing ${key}`); process.exit(2); } const call = async (url, key, body) => { const response = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json', authorization: `Bearer ${key}` }, body: JSON.stringify(body) }); if (!response.ok) throw new Error(`live endpoint HTTP ${response.status}`); const data = await response.json(); if (!data || (!Array.isArray(data.data) && !Array.isArray(data.results))) throw new Error('live endpoint shape invalid'); return data; }; try { await call(process.env.RAG_EMBEDDING_URL, process.env.RAG_EMBEDDING_API_KEY, { model: process.env.RAG_EMBEDDING_MODEL, input: 'rag-eval health check' }); await call(process.env.RAG_RERANK_URL, process.env.RAG_RERANK_API_KEY, { model: process.env.RAG_RERANK_MODEL, query: 'rag-eval health check', documents: ['health check'] }); console.log('RAG live endpoint validation PASS'); } catch (error) { console.error(`RAG live evaluation failed: ${error.message}`); process.exit(2); } }
const result = run(); const failures = fails(result.metrics); const report = { schemaVersion: 3, mode: 'offline-deterministic', metrics: result.metrics, thresholds, failures, cases: result.rows };
const reportsDir = path.join(base, 'reports'); fs.mkdirSync(reportsDir, { recursive: true }); fs.writeFileSync(path.join(reportsDir, 'latest.json'), JSON.stringify(report, null, 2) + '\n'); fs.writeFileSync(path.join(reportsDir, 'latest.md'), `# RAG retrieval eval\n\nMode: offline-deterministic\n\nResult: ${failures.length ? 'FAIL' : 'PASS'}\n\n${Object.entries(result.metrics).map(([k, v]) => `- ${k}: ${typeof v === 'number' ? v.toFixed(3) : v}`).join('\n')}\n`); console.log(`RAG eval ${failures.length ? 'FAIL' : 'PASS'}: ${JSON.stringify(result.metrics)}`); if (failures.length) process.exit(1);
