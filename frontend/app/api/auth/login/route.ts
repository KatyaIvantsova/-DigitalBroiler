import { NextResponse } from "next/server"
import { AUTH_COOKIE } from "@/lib/auth"
import { springApi } from "@/lib/spring-api"

export const dynamic = "force-dynamic"

export async function POST(request: Request) {
  const body = await request.text()
  const response = await springApi("/api/v1/auth/login", {
    method: "POST",
    body: body || undefined,
  })

  if (!response.ok) {
    const message = response.status === 401 || response.status === 400
      ? "Неверный логин или пароль"
      : "Сервер недоступен, попробуйте позже"
    return NextResponse.json({ error: message }, { status: response.status })
  }

  const { accessToken, expiresAt, user } = await response.json()
  const result = NextResponse.json({ user })

  result.cookies.set(AUTH_COOKIE, accessToken, {
    httpOnly: true,
    sameSite: "lax",
    // Включите AUTH_COOKIE_SECURE=true, когда сайт работает по HTTPS.
    secure: process.env.AUTH_COOKIE_SECURE === "true",
    path: "/",
    expires: new Date(expiresAt),
  })

  return result
}
