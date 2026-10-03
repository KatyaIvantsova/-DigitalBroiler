import { cookies } from "next/headers"
import { AUTH_COOKIE } from "@/lib/auth"

const DEFAULT_SPRING_API_URL = "http://localhost:8080"

async function sessionToken(): Promise<string | undefined> {
  try {
    const store = await cookies()
    return store.get(AUTH_COOKIE)?.value
  } catch {
    // Вызов вне запроса (например, при сборке) — без токена.
    return undefined
  }
}

export async function springApi(path: string, init?: RequestInit) {
  const baseUrl = process.env.SPRING_API_URL ?? DEFAULT_SPRING_API_URL
  const headers = new Headers(init?.headers)
  const hasFormDataBody =
    typeof FormData !== "undefined" && init?.body instanceof FormData

  if (!hasFormDataBody && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json")
  }

  if (!headers.has("Authorization")) {
    const token = await sessionToken()
    if (token) {
      headers.set("Authorization", `Bearer ${token}`)
    }
  }

  return fetch(`${baseUrl}${path}`, {
    ...init,
    headers,
    cache: "no-store",
  })
}
