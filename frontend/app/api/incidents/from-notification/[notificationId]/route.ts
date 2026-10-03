import { springApi } from "@/lib/spring-api"

export const dynamic = "force-dynamic"

export async function POST(
  request: Request,
  context: { params: Promise<{ notificationId: string }> },
) {
  const { notificationId } = await context.params
  const body = await request.text()
  const response = await springApi(`/api/v1/incidents/from-notification/${notificationId}`, {
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
