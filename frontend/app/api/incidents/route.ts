import { springApi } from "@/lib/spring-api"

export const dynamic = "force-dynamic"

export async function GET() {
  const response = await springApi("/api/v1/incidents")
  const body = await response.text()

  return new Response(body || null, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("Content-Type") ?? "application/json",
    },
  })
}

export async function POST(request: Request) {
  const body = await request.text()
  const response = await springApi("/api/v1/incidents", {
    method: "POST",
    body: body || undefined,
  })
  const responseBody = await response.text()

  return new Response(responseBody || null, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("Content-Type") ?? "application/json",
    },
  })
}
