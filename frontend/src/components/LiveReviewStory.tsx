import { useEffect, useMemo, useState } from 'react';

interface LiveReviewStoryProps {
  onExit: () => void;
}

const STEPS = [
  { label: 'PR #128', detail: 'Ingest', duration: '1.2s', note: 'The pull request is pinned to an immutable commit before analysis begins.' },
  { label: 'Index', detail: 'Vectorized', duration: '2.8s', note: 'The repository snapshot is chunked and indexed with commit-level isolation.' },
  { label: 'Hybrid RAG', detail: 'Retrieved', duration: '3.1s', note: 'Vector and keyword retrieval are fused, reranked, and trimmed to an evidence budget.' },
  { label: 'AI-1 Plan', detail: 'Planned', duration: '2.4s', note: 'The planner turns the diff and repository context into a bounded review plan.' },
  { label: 'AI-2 Review', detail: 'Generated', duration: '4.3s', note: 'The executor follows the plan and produces structured candidate findings.' },
  { label: 'Evidence Gate', detail: 'Verifying', duration: '1.0s', note: 'The gate rejects unsupported claims before they reach GitHub.' },
  { label: 'Human Publish', detail: 'Pending', duration: '—', note: 'A reviewer chooses which comments are safe and useful to publish.' },
];

const CODE_LINES = [
  ['79', 'public List<User> findByEmail(String email) throws SQLException {', ''],
  ['80', '  List<User> users = new ArrayList<>();', ''],
  ['81', '  String sql = "SELECT id, name, email, role FROM users ";', ''],
  ['82', '  sql += "WHERE email = \'" + email + "\'";', 'finding'],
  ['83', '  try (Connection conn = dataSource.getConnection();', ''],
  ['84', '       Statement stmt = conn.createStatement();', ''],
  ['85', '       ResultSet rs = stmt.executeQuery(sql)) {', ''],
  ['86', '    while (rs.next()) {', ''],
  ['87', '      users.add(mapRow(rs));', ''],
  ['88', '    }', ''],
  ['89', '  }', ''],
  ['90', '  return users;', ''],
  ['91', '}', ''],
  ['92', '', ''],
  ['93', 'private User mapRow(ResultSet rs) throws SQLException {', ''],
];

const EVIDENCE = [
  {
    rank: 1,
    path: 'src/main/java/com/acme/user/UserRepository.java',
    lines: 'Lines 80–88',
    score: 94,
    excerpt: 'sql += "WHERE email = \'" + email + "\'";\nStatement stmt = conn.createStatement();',
    rule: 'java/sql-injection · Use prepared statements or parameter binding.',
    source: 'Secure Coding Guidelines (v2.1)',
  },
  {
    rank: 2,
    path: 'src/main/java/com/acme/user/AdminService.java',
    lines: 'Lines 112–118',
    score: 82,
    excerpt: 'String q = "SELECT * FROM users WHERE role = \'" + role + "\'";\nStatement s = conn.createStatement();',
    rule: 'Cross-file pattern · The same unsafe query construction appears here.',
    source: 'Repository context at 9c4a7e1',
  },
];

export function LiveReviewStory({ onExit }: LiveReviewStoryProps) {
  const [activeStep, setActiveStep] = useState(5);
  const [playing, setPlaying] = useState(false);
  const [openEvidence, setOpenEvidence] = useState<Set<number>>(() => new Set([1, 2]));
  const [showComments, setShowComments] = useState(false);

  useEffect(() => {
    if (!playing) return;
    const timer = window.setTimeout(() => {
      setActiveStep((current) => {
        if (current >= STEPS.length - 1) {
          setPlaying(false);
          return current;
        }
        return current + 1;
      });
    }, 1150);
    return () => window.clearTimeout(timer);
  }, [activeStep, playing]);

  const progress = useMemo(
    () => `${(activeStep / (STEPS.length - 1)) * 100}%`,
    [activeStep],
  );

  function restart() {
    setShowComments(false);
    setActiveStep(0);
    setPlaying(true);
  }

  function toggleEvidence(rank: number) {
    setOpenEvidence((current) => {
      const next = new Set(current);
      if (next.has(rank)) next.delete(rank);
      else next.add(rank);
      return next;
    });
  }

  return (
    <section className="story" aria-label="Interactive AI review story">
      <header className="story-header">
        <div>
          <h1>Live Review Story</h1>
          <p>AI agents collaborate with repository evidence and human judgment.</p>
        </div>
        <div className="story-controls">
          <button type="button" className="story-button story-button--quiet" onClick={onExit}>Exit demo</button>
          <button
            type="button"
            className="story-button story-button--icon"
            onClick={() => setPlaying((value) => !value)}
            aria-label={playing ? 'Pause story' : 'Play story'}
          >
            {playing ? 'Pause' : 'Play'}
          </button>
          <button type="button" className="story-button story-button--quiet" onClick={restart}>Restart</button>
        </div>
      </header>

      <div className="story-pipeline" aria-label="Review pipeline">
        <div className="story-progress-track" aria-hidden="true">
          <span className="story-progress-fill" style={{ width: progress }} />
        </div>
        <div className="story-steps">
          {STEPS.map((step, index) => {
            const state = index < activeStep ? 'complete' : index === activeStep ? 'active' : 'pending';
            return (
              <button
                key={step.label}
                type="button"
                className={`story-step story-step--${state}`}
                onClick={() => { setActiveStep(index); setPlaying(false); }}
                aria-current={index === activeStep ? 'step' : undefined}
              >
                <span className="story-step-number">{index < activeStep ? '✓' : index + 1}</span>
                <span className="story-step-copy">
                  <strong>{step.label}</strong>
                  <small>{step.detail}</small>
                </span>
                <span className="story-step-duration">{step.duration}</span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="story-note" role="status">
        <span className="story-note-mark" aria-hidden="true">i</span>
        <span key={activeStep}>{STEPS[activeStep].note}</span>
      </div>

      <div className="story-stage">
        <section className="story-code" aria-label="Pull request code diff">
          <header className="story-panel-header">
            <div><strong>Diff:</strong> <span>src/main/java/com/acme/user/UserRepository.java</span></div>
            <div className="story-diff-count"><span>+23</span><span>−6</span></div>
          </header>
          <div className="story-code-body" role="table" aria-label="Java code diff">
            {CODE_LINES.map(([line, code, state]) => (
              <div key={line} className={`story-code-line${state ? ` story-code-line--${state}` : ''}`} role="row">
                <span className="story-code-number" role="cell">{line}</span>
                <code role="cell">{code || ' '}</code>
                {state === 'finding' && <span className="story-finding-pin">SQL injection</span>}
              </div>
            ))}
          </div>
          <footer className="story-code-footer">
            <span>1 finding on line 82</span>
            <span className="story-risk">High</span>
            <span>SQL Injection</span>
          </footer>
        </section>

        <section className="story-evidence" aria-label="Retrieval evidence inspector">
          <header className="story-panel-header story-panel-header--evidence">
            <div><span className="story-shield" aria-hidden="true">✓</span><strong>Why this finding is trusted</strong></div>
            <span>Cross-file evidence · 2</span>
          </header>
          <div className="story-evidence-list">
            {EVIDENCE.map((item) => {
              const open = openEvidence.has(item.rank);
              return (
                <article className={`story-evidence-item${open ? ' story-evidence-item--open' : ''}`} key={item.rank}>
                  <button type="button" className="story-evidence-trigger" onClick={() => toggleEvidence(item.rank)} aria-expanded={open}>
                    <span className="story-evidence-rank">{item.rank}</span>
                    <span className="story-evidence-title"><strong>{item.path}</strong><small>{item.lines}</small></span>
                    <span className="story-score"><span>Relevance {item.score / 100}</span><i><b style={{ width: `${item.score}%` }} /></i></span>
                    <span className={`story-caret${open ? ' story-caret--open' : ''}`} aria-hidden="true" />
                  </button>
                  {open && (
                    <div className="story-evidence-body">
                      <pre>{item.excerpt}</pre>
                      <p><strong>Rule:</strong> {item.rule}</p>
                      <footer><span>Source: {item.source}</span><span className="story-verified">✓ Verified</span></footer>
                    </div>
                  )}
                </article>
              );
            })}
          </div>
        </section>
      </div>

      <footer className="story-results">
        <div className="story-result"><strong>3</strong><span>findings</span></div>
        <div className="story-result story-result--danger"><strong>1</strong><span>high risk</span></div>
        <div className="story-result story-result--accent"><strong>2</strong><span>evidence-backed</span></div>
        <div className="story-ready"><span className="story-ready-mark">✓</span><span>Ready for<br />human review</span></div>
        <button type="button" className="story-primary" onClick={() => { setShowComments(true); setActiveStep(6); setPlaying(false); }}>
          Review comments <span aria-hidden="true">→</span>
        </button>
      </footer>

      {showComments && (
        <div className="story-drawer" role="dialog" aria-modal="true" aria-labelledby="story-comments-title">
          <button type="button" className="story-drawer-backdrop" onClick={() => setShowComments(false)} aria-label="Close comments" />
          <div className="story-drawer-panel">
            <header><div><h2 id="story-comments-title">Human review</h2><p>Only selected, evidence-backed comments will be published.</p></div><button type="button" onClick={() => setShowComments(false)}>Close</button></header>
            <label className="story-comment"><input type="checkbox" defaultChecked /><span><strong>UserRepository.java:82</strong><small>High · SQL Injection</small><p>Build the query with a PreparedStatement and bind <code>email</code> as a parameter. Repository evidence shows the same unsafe pattern in AdminService.</p></span></label>
            <label className="story-comment"><input type="checkbox" defaultChecked /><span><strong>AdminService.java:112</strong><small>Medium · Maintainability</small><p>Extract the repeated query construction into a parameterized repository method to prevent the pattern from spreading.</p></span></label>
            <button type="button" className="story-primary story-primary--publish" onClick={() => setShowComments(false)}>Approve 2 comments for GitHub</button>
          </div>
        </div>
      )}
    </section>
  );
}
