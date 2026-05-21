const CACHE_PREFIX = 'mailbox-cache-v1';
const CACHE_TTL_MS = 1000 * 60 * 60 * 12;
const AI_HISTORY_LIMIT = 8;

function buildKey(type, key) {
  return `${CACHE_PREFIX}:${type}:${key}`;
}

function readCache(type, key) {
  try {
    const raw = localStorage.getItem(buildKey(type, key));
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw);
    if (!parsed?.savedAt || Date.now() - parsed.savedAt > CACHE_TTL_MS) {
      localStorage.removeItem(buildKey(type, key));
      return null;
    }
    return parsed.payload ?? null;
  } catch {
    return null;
  }
}

function writeCache(type, key, payload) {
  try {
    localStorage.setItem(
      buildKey(type, key),
      JSON.stringify({
        savedAt: Date.now(),
        payload
      })
    );
  } catch {
    // Ignore quota and serialization errors.
  }
}

export function getMailListCache(cacheKey) {
  return cacheKey ? readCache('mail-list', cacheKey) : null;
}

export function setMailListCache(cacheKey, payload) {
  if (cacheKey) {
    writeCache('mail-list', cacheKey, payload);
  }
}

export function getMailListQueryCache(cacheKey) {
  return cacheKey ? readCache('mail-query', cacheKey) : null;
}

export function setMailListQueryCache(cacheKey, payload) {
  if (cacheKey) {
    writeCache('mail-query', cacheKey, payload);
  }
}

export function getMailDetailCache(mailType, mailId) {
  return mailId ? readCache('mail-detail', `${mailType}:${mailId}`) : null;
}

export function setMailDetailCache(mailType, mailId, payload) {
  if (mailId) {
    writeCache('mail-detail', `${mailType}:${mailId}`, payload);
  }
}

export function getAiResultCache(kind, mailId, extra = '') {
  return mailId ? readCache('ai-result', `${kind}:${mailId}:${extra}`) : null;
}

export function setAiResultCache(kind, mailId, payload, extra = '') {
  if (mailId) {
    writeCache('ai-result', `${kind}:${mailId}:${extra}`, payload);
  }
}

export function getAiHistory(mailId) {
  return mailId ? readCache('ai-history', String(mailId)) || [] : [];
}

export function pushAiHistory(mailId, entry) {
  if (!mailId || !entry) {
    return;
  }
  const current = getAiHistory(mailId);
  const next = [
    {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      createdAt: Date.now(),
      ...entry
    },
    ...current
  ].slice(0, AI_HISTORY_LIMIT);
  writeCache('ai-history', String(mailId), next);
}
