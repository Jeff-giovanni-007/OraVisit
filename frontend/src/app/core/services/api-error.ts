export function apiErrorMessage(error: any, fallback: string): string {
  const body = error?.error;
  const fieldMessages = Array.isArray(body?.errors)
    ? body.errors.map((e: { message?: string }) => e.message).filter(Boolean)
    : [];
  if (fieldMessages.length) return fieldMessages.join(' ');
  return body?.detail || body?.message || fallback;
}
