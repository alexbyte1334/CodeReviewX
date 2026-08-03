/// <reference types="vite/client" />
interface Window {
  codereviewx?: {
    saveCredentials: (config: unknown) => void;
    clearCredentials: () => void;
    restart: () => void;
  };
}
