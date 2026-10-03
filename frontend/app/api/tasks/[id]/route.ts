// app/api/tasks/[id]/route.ts
import { springApi } from "@/lib/spring-api"

export const dynamic = "force-dynamic"

export async function PUT(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  // ✅ ВАЖНО: нужно await params в Next.js 16
  const { id } = await params
  
  console.log("=== PUT /api/tasks/[id] ===")
  console.log("Task ID:", id)
  
  if (!id) {
    return new Response(JSON.stringify({ error: "Task ID is required" }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    })
  }
  
  const body = await request.text()
  console.log("Body:", body)
  
  const response = await springApi(`/api/v1/task/${id}`, {
    method: "PUT",
    body: body || undefined,
  })
  
  const responseBody = await response.text()
  console.log("Response status:", response.status)
  console.log("Response body:", responseBody)
  
  return new Response(responseBody || null, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("Content-Type") ?? "application/json",
    },
  })
}

export async function PATCH(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params
  const body = await request.text()

  const response = await springApi(`/api/v1/task/${id}`, {
    method: "PATCH",
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

export async function DELETE(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  // ✅ ВАЖНО: нужно await params в Next.js 16
  const { id } = await params
  
  console.log("=== DELETE /api/tasks/[id] ===")
  console.log("Task ID:", id)
  
  if (!id) {
    return new Response(JSON.stringify({ error: "Task ID is required" }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    })
  }
  
  const response = await springApi(`/api/v1/task/${id}`, {
    method: "DELETE",
  })
  
  const responseBody = await response.text()
  console.log("Response status:", response.status)
  console.log("Response body:", responseBody)
  
  return new Response(responseBody || null, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("Content-Type") ?? "application/json",
    },
  })
}