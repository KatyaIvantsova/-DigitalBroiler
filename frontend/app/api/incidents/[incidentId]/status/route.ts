import { springApi } from "@/lib/spring-api"

export const dynamic = "force-dynamic"

export async function PATCH(request: Request, context: { params: Promise<{ incidentId: string }> }) {
  const { incidentId } = await context.params
  const body = await request.text()

  const response = await springApi(`/api/v1/incidents/${incidentId}/status`, {
    method: "PATCH",
    body: body || JSON.stringify(""),
    headers: { "Content-Type": "application/json" },
  })
  const responseBody = await response.text()

  return new Response(responseBody || null, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("Content-Type") ?? "application/json",
    },
  })
}