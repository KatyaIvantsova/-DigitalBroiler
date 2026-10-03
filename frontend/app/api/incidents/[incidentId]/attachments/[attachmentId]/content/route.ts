import { springApi } from "@/lib/spring-api"

export const dynamic = "force-dynamic"

export async function GET(
  _request: Request,
  context: { params: Promise<{ incidentId: string; attachmentId: string }> },
) {
  const { incidentId, attachmentId } = await context.params
  const response = await springApi(
    `/api/v1/incidents/${encodeURIComponent(incidentId)}/attachments/${encodeURIComponent(attachmentId)}/content`,
  )

  const headers = new Headers()
  const contentType = response.headers.get("Content-Type")
  const contentDisposition = response.headers.get("Content-Disposition")
  const contentLength = response.headers.get("Content-Length")

  if (contentType) headers.set("Content-Type", contentType)
  if (contentDisposition) headers.set("Content-Disposition", contentDisposition)
  if (contentLength) headers.set("Content-Length", contentLength)

  return new Response(response.body, {
    status: response.status,
    headers,
  })
}
