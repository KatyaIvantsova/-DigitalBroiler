"use client"

import { useEffect, useState } from "react"
import type { CurrentUser } from "@/lib/auth"

export function useCurrentUser() {
  const [user, setUser] = useState<CurrentUser | null>(null)

  useEffect(() => {
    let cancelled = false
    fetch("/api/auth/me")
      .then((response) => {
        if (response.status === 401) {
          window.location.href = "/login"
          return null
        }
        return response.ok ? response.json() : null
      })
      .then((data) => {
        if (!cancelled && data) setUser(data)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  return user
}

export async function logout() {
  await fetch("/api/auth/logout", { method: "POST" }).catch(() => {})
  window.location.href = "/login"
}
