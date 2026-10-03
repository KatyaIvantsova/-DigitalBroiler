import { springApi } from "@/lib/spring-api"

export const dynamic = "force-dynamic"

export async function GET(
  _request: Request,
  context: { params: Promise<{ incidentId: string }> },
) {
  const { incidentId } = await context.params
  const response = await springApi(`/api/v1/incidents/${incidentId}/attachments`)
  const body = await response.text()

  return new Response(body || null, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("Content-Type") ?? "application/json",
    },
  })
}

export async function POST(
  request: Request,
  context: { params: Promise<{ incidentId: string }> },
) {
  const { incidentId } = await context.params
  const formData = await request.formData()
  const response = await springApi(`/api/v1/incidents/${incidentId}/attachments`, {
    method: "POST",
    body: formData,
  })
  const responseBody = await response.text()

  return new Response(responseBody || null, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("Content-Type") ?? "application/json",
    },
  })
}
