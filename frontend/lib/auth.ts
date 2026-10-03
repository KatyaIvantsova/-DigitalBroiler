export const AUTH_COOKIE = "broiler_session"

export type CurrentUser = {
  id: string
  username: string
  fullName: string
  position: string
  role: "OPERATOR" | "TECHNOLOGIST" | "VETERINARIAN" | "MANAGER" | "ADMIN"
}

export const ROLE_LABELS: Record<CurrentUser["role"], string> = {
  OPERATOR: "Оператор",
  TECHNOLOGIST: "Технолог",
  VETERINARIAN: "Ветеринар",
  MANAGER: "Руководитель",
  ADMIN: "Администратор",
}

// Срок жизни берётся из JWT без проверки подписи: подпись проверяет backend,
// здесь это только быстрый отсев заведомо истёкших сессий.
export function isTokenExpired(token: string, nowMs = Date.now()): boolean {
  const payload = token.split(".")[1]
  if (!payload) return true

  try {
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/")
    const json = JSON.parse(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "=")))
    return typeof json.exp !== "number" || json.exp * 1000 <= nowMs
  } catch {
    return true
  }
}
