import { useEffect, useState } from 'react';

export type ColorTheme = 'light' | 'dark';

const STORAGE_KEY = 'codereviewx-theme';

function readStoredTheme(): ColorTheme | null {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'dark' || saved === 'light') return saved;
  } catch {
    /* storage unavailable in test/runtime */
  }
  return null;
}

function resolveInitialTheme(): ColorTheme {
  if (typeof window === 'undefined') return 'light';
  const saved = readStoredTheme();
  if (saved) return saved;
  if (typeof window.matchMedia === 'function') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  return 'light';
}

/** Apply persisted theme before React mount to reduce flash. */
export function applyStoredTheme(): void {
  document.documentElement.setAttribute('data-theme', resolveInitialTheme());
}

export function useColorTheme() {
  const [theme, setTheme] = useState<ColorTheme>(resolveInitialTheme);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      /* storage unavailable */
    }
  }, [theme]);

  function toggleTheme() {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));
  }

  return { theme, toggleTheme, isDark: theme === 'dark' };
}
