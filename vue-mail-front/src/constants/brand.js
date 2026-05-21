/** Keep in sync with backend mail-system.email-domain. */

export const MAIL_SYSTEM_NAME =
  typeof import.meta.env.VITE_MAIL_SYSTEM_NAME === 'string' && import.meta.env.VITE_MAIL_SYSTEM_NAME.trim()
    ? import.meta.env.VITE_MAIL_SYSTEM_NAME.trim()
    : 'L-email 系统';

export const MAIL_EMAIL_DOMAIN =
  typeof import.meta.env.VITE_MAIL_EMAIL_DOMAIN === 'string' && import.meta.env.VITE_MAIL_EMAIL_DOMAIN.trim()
    ? import.meta.env.VITE_MAIL_EMAIL_DOMAIN.trim().toLowerCase()
    : 'lmailbox.com';

export const MAIL_EMAIL_SUFFIX = `@${MAIL_EMAIL_DOMAIN}`;

/** Build a full mailbox address from the local part only. */
export function buildMailboxEmail(localPart) {
  const local = String(localPart ?? '')
    .trim()
    .toLowerCase();
  return `${local}@${MAIL_EMAIL_DOMAIN}`;
}
