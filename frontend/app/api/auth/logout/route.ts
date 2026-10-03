import { NextResponse } from "next/server"
import { AUTH_COOKIE } from "@/lib/auth"

export const dynamic = "force-dynamic"

export async function POST() {
  const result = NextResponse.json({ ok: true })
  result.cookies.delete(AUTH_COOKIE)
  return result
}
