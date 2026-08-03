import { useState } from 'react';
import { ReviewRunWorkspace } from './components/ReviewRunWorkspace';
import { SetupWizard } from './components/SetupWizard';
import './styles/app.css';

export default function App() {
  const [complete, setComplete] = useState(() => localStorage.getItem('codereviewx.setup-complete') === 'true');
  if (!complete) return <SetupWizard onComplete={() => setComplete(true)} />;
  const reconfigure = () => {
    window.codereviewx?.clearCredentials();
    localStorage.removeItem('codereviewx.setup-complete');
    if (window.codereviewx?.restart) window.codereviewx.restart(); else setComplete(false);
  };
  return <><button className="setup-reconfigure" type="button" onClick={reconfigure}>重新配置 / 清除本地凭证</button><ReviewRunWorkspace /></>;
}
