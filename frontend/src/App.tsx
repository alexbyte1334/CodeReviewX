import { useState } from 'react';
import { LiveReviewStory } from './components/LiveReviewStory';
import { ReviewRunWorkspace } from './components/ReviewRunWorkspace';
import { ThemeToggle } from './components/ThemeToggle';
import { useColorTheme } from './hooks/useColorTheme';
import './styles/app.css';

export default function App() {
  const { theme, toggleTheme } = useColorTheme();
  const [route, setRoute] = useState(() => window.location.hash === '#/demo' || import.meta.env.MODE === 'pages' ? 'demo' : 'workspace');
  return <div className={`app-root${route === 'demo' ? ' app-root--demo' : ''}`}>
    <div className="app-vibrancy-bg" aria-hidden="true"><div className="vibrancy-mesh" /><div className="vibrancy-noise" /></div>
    <div className="app-shell">
      <aside className="sidebar" aria-label="Product navigation">
        <div className="sidebar-brand"><div className="sidebar-logo" aria-hidden="true">✓</div><div><p className="sidebar-product-name">CodeReviewX</p><p className="sidebar-product-tag">Evidence-grounded AI code review workspace</p></div></div>
        <nav className="sidebar-nav" aria-label="Main navigation">
          <button type="button" className={`sidebar-nav-item${route === 'workspace' ? ' sidebar-nav-item--active' : ''}`} onClick={() => { setRoute('workspace'); window.history.replaceState(null, '', '#/reviews/new'); }}>Review Workspace</button>
          <button type="button" className={`sidebar-nav-item${route === 'demo' ? ' sidebar-nav-item--active' : ''}`} onClick={() => { setRoute('demo'); window.history.replaceState(null, '', '#/demo'); }}>Trusted Demo</button>
        </nav>
        <div className="sidebar-footer"><p className="sidebar-product-tag">Live runs are queued, resumable and human-approved before GitHub publishing.</p></div>
      </aside>
      <div className="main-column">
        <header className="window-chrome"><div className="window-traffic-lights" aria-hidden="true"><span className="traffic-light traffic-light--close" /><span className="traffic-light traffic-light--minimize" /><span className="traffic-light traffic-light--maximize" /></div><div className="window-chrome-trailing"><ThemeToggle theme={theme} onToggle={toggleTheme} /></div></header>
        {route === 'demo' ? <main className="workspace workspace--story"><LiveReviewStory /></main> : <ReviewRunWorkspace />}
      </div>
    </div>
  </div>;
}
