import { springApi } from "@/lib/spring-api"

export const dynamic = "force-dynamic"

const BACKEND_TIMEOUT_MS = 3000

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url)

  // Валидация на фронте — быстрый 400 без похода в backend
  const requestedDays = Number(searchParams.get("periodDays"))
  const periodDays = requestedDays === 30 ? 30 : 7

  const slaMinutes = Number(searchParams.get("slaMinutes")) || 30
  if (slaMinutes !== 30) {
    return Response.json(
      { error: "slaMinutes must be 30" },
      { status: 400 }
    )
  }

  const workshop = searchParams.get("workshop") || null
  const house = searchParams.get("house") || null

  // Собираем query для backend
  const backendParams = new URLSearchParams()
  backendParams.set("periodDays", String(periodDays))
  backendParams.set("slaMinutes", String(slaMinutes))
  if (workshop) backendParams.set("workshop", workshop)
  if (house) backendParams.set("house", house)

  const path = `/api/v1/incidents/analytics?${backendParams.toString()}`

  // S4-08: без подстановки демо-данных. Нет связи с backend — честное 503 с понятным текстом.
  try {
    const response = await withTimeout(springApi(path), BACKEND_TIMEOUT_MS)
    if (response.status >= 500) {
      console.warn(`[analytics] backend returned ${response.status}`)
      return noConnection(`Сервер ответил ошибкой (${response.status}). Повторите запрос позже.`)
    }

    const body = await response.text()
    return new Response(body || null, {
      status: response.status,
      headers: { "Content-Type": response.headers.get("Content-Type") ?? "application/json" },
    })
  } catch (error) {
    console.warn("[analytics] backend unavailable:", error instanceof Error ? error.message : error)
    return noConnection("Нет связи с сервером. Данные аналитики не загружены.")
  }
}

// ── helpers ────────────────────────────────────────────────────────────────

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) => setTimeout(() => reject(new Error(`Timeout after ${ms}ms`)), ms)),
  ])
}

function noConnection(message: string) {
  return Response.json({ message }, { status: 503 })
}
