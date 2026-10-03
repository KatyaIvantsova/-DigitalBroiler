import { springApi } from "@/lib/spring-api"

export const dynamic = "force-dynamic"

type Context = { params: Promise<{ path: string[] }> }

// Общий прокси к REST API backend для новых экранов: /api/v1/* → SPRING_API_URL/api/v1/*.
// Токен из httpOnly-cookie подставляет springApi; права проверяет backend.
async function forward(request: Request, context: Context) {
  const { path } = await context.params
  const { search } = new URL(request.url)
  const target = `/api/v1/${path.map(encodeURIComponent).join("/")}${search}`

  const hasBody = request.method !== "GET" && request.method !== "HEAD"
  const contentType = request.headers.get("Content-Type")
  const headers = new Headers()
  if (contentType) headers.set("Content-Type", contentType)

  const response = await springApi(target, {
    method: request.method,
    headers,
    body: hasBody ? await request.arrayBuffer() : undefined,
  })

  const passHeaders = new Headers()
  for (const name of ["Content-Type", "Content-Disposition"]) {
    const value = response.headers.get(name)
    if (value) passHeaders.set(name, value)
  }
  const body = response.status === 204 ? null : await response.arrayBuffer()
  return new Response(body, { status: response.status, headers: passHeaders })
}

export const GET = forward
export const POST = forward
export const PUT = forward
export const PATCH = forward
export const DELETE = forward
