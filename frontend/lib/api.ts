"use client"

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
  }
}

// Текст ошибки для пользователя: сообщение backend (ResponseStatusException) или ошибки валидации полей.
async function errorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json()
    if (Array.isArray(body?.errors) && body.errors.length > 0) {
      return body.errors
        .map((error: { field?: string; defaultMessage?: string }) =>
          error.field ? `${error.field}: ${error.defaultMessage}` : error.defaultMessage,
        )
        .join("; ")
    }
    if (typeof body?.message === "string" && body.message) return body.message
    if (typeof body?.error === "string" && body.error) return body.error
  } catch {
    // тело не JSON
  }
  if (response.status === 403) return "Недостаточно прав"
  if (response.status === 404) return "Не найдено"
  return `Ошибка сервера (${response.status})`
}

export async function api<T = unknown>(path: string, init?: RequestInit & { json?: unknown }): Promise<T> {
  const { json, ...rest } = init ?? {}
  const response = await fetch(`/api/v1${path}`, {
    ...rest,
    headers: json !== undefined ? { "Content-Type": "application/json", ...rest.headers } : rest.headers,
    body: json !== undefined ? JSON.stringify(json) : rest.body,
  })
  if (response.status === 401) {
    window.location.href = `/login?next=${encodeURIComponent(window.location.pathname)}`
    throw new ApiError("Требуется вход", 401)
  }
  if (!response.ok) {
    throw new ApiError(await errorMessage(response), response.status)
  }
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
