import { NextResponse } from "next/server"
import type { NextRequest } from "next/server"
import { AUTH_COOKIE, isTokenExpired } from "@/lib/auth"

const PUBLIC_PATHS = ["/login", "/api/auth/login", "/api/auth/logout"]

// Оптимистичная проверка сессии: без cookie или с истёкшим токеном
// страницы уводят на /login, а API-роуты отвечают 401. Подпись токена
// проверяет backend на каждом запросе.
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl

  if (PUBLIC_PATHS.includes(pathname)) {
    return NextResponse.next()
  }

  const token = request.cookies.get(AUTH_COOKIE)?.value
  if (token && !isTokenExpired(token)) {
    return NextResponse.next()
  }

  if (pathname.startsWith("/api/")) {
    const response = NextResponse.json({ error: "Требуется вход" }, { status: 401 })
    if (token) response.cookies.delete(AUTH_COOKIE)
    return response
  }

  const loginUrl = new URL("/login", request.url)
  if (pathname !== "/") {
    loginUrl.searchParams.set("next", `${pathname}${search}`)
  }
  const response = NextResponse.redirect(loginUrl)
  if (token) response.cookies.delete(AUTH_COOKIE)
  return response
}

export const config = {
  // Всё, кроме статики Next.js и файлов из public/
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:png|jpg|jpeg|svg|ico|webp)$).*)"],
}
