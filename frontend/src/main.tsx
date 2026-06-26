import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { applyStoredTheme } from './hooks/useColorTheme';
import App from './App';

applyStoredTheme();

const rootEl = document.getElementById('root');
if (!rootEl) throw new Error('Root element not found');

createRoot(rootEl).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
