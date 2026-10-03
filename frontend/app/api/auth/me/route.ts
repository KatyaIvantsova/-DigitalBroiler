import { springApi } from "@/lib/spring-api"

export const dynamic = "force-dynamic"

export async function GET() {
  const response = await springApi("/api/v1/auth/me")
  const body = await response.text()

  return new Response(body || null, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("Content-Type") ?? "application/json",
    },
  })
}
