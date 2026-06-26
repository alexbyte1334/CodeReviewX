import type { ColorTheme } from '../hooks/useColorTheme';

interface ThemeToggleProps {
  theme: ColorTheme;
  onToggle: () => void;
}

export function ThemeToggle({ theme, onToggle }: ThemeToggleProps) {
  const isDark = theme === 'dark';
  const label = isDark ? 'Switch to light mode' : 'Switch to dark mode';

  return (
    <button
      type="button"
      className="theme-toggle"
      onClick={onToggle}
      aria-label={label}
      aria-pressed={isDark}
      title={label}
    >
      <span className="theme-toggle-track" aria-hidden="true">
        <span className={`theme-toggle-thumb${isDark ? ' theme-toggle-thumb--dark' : ''}`}>
          {isDark ? (
            <svg viewBox="0 0 20 20" width="12" height="12" fill="currentColor">
              <path d="M10 2a1 1 0 0 1 1 1v1.06A5.002 5.002 0 0 1 15.94 9H17a1 1 0 1 1 0 2h-1.06A5.002 5.002 0 0 1 11 15.94V17a1 1 0 1 1-2 0v-1.06A5.002 5.002 0 0 1 4.06 11H3a1 1 0 0 1 0-2h1.06A5.002 5.002 0 0 1 9 4.06V3a1 1 0 0 1 1-1Zm0 3.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7Z" />
            </svg>
          ) : (
            <svg viewBox="0 0 20 20" width="12" height="12" fill="currentColor">
              <path d="M10.5 2.5a1 1 0 0 0-1 1v.09a6.5 6.5 0 0 0-5.91 5.91H3a1 1 0 1 0 0 2h.59A6.5 6.5 0 0 0 9.5 16.41V17a1 1 0 1 0 2 0v-.59A6.5 6.5 0 0 0 17.41 9.5H18a1 1 0 1 0 0-2h-.59A6.5 6.5 0 0 0 11.5 3.59V3.5a1 1 0 0 0-1-1Zm-1 3a5 5 0 1 1 0 10 5 5 0 0 1 0-10Z" />
            </svg>
          )}
        </span>
      </span>
      <span className="theme-toggle-label">{isDark ? 'Dark' : 'Light'}</span>
    </button>
  );
}
