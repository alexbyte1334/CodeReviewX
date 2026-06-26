import { describe, it, expect } from 'vitest';
import { formatProviderHitLabel } from '../utils/providerHit';

describe('formatProviderHitLabel', () => {
  it('returns hit label when provider matched', () => {
    expect(formatProviderHitLabel(true, 'mimo', 'mimo')).toBe('命中 · MiMo 已生效');
  });

  it('labels historical mock responses without recommending fallback', () => {
    expect(formatProviderHitLabel(false, 'mimo', 'mock')).toBe(
      '未命中 · 请求 MiMo，实际使用 Historical Mock',
    );
  });
});
