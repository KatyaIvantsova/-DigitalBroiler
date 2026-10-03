"use client"

import { useCallback, useEffect, useState } from "react"
import { api } from "@/lib/api"

// Загрузка данных с повторным запросом по reload(); path = null — не загружать.
export function useApi<T>(path: string | null) {
  const [data, setData] = useState<T>()
  const [error, setError] = useState<string>()
  const [version, setVersion] = useState(0)

  useEffect(() => {
    if (!path) return
    let cancelled = false
    api<T>(path)
      .then((result) => {
        if (!cancelled) {
          setData(result)
          setError(undefined)
        }
      })
      .catch((reason: Error) => {
        if (!cancelled) setError(reason.message)
      })
    return () => {
      cancelled = true
    }
  }, [path, version])

  const reload = useCallback(() => setVersion((value) => value + 1), [])
  return { data, error, loading: data === undefined && error === undefined, reload }
}
