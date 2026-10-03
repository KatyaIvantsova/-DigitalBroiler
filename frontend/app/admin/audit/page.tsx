"use client"

import { useState } from "react"
import { AppShell, ErrorNote, Panel } from "@/components/app/app-shell"
import { AuditList } from "@/components/app/audit-list"
import { useApi } from "@/hooks/use-api"
import type { AuditEntry } from "@/lib/types"

const TYPES: Record<string, string> = {
  "": "Все",
  FLOCK: "Партии и учёт",
  INCIDENT: "Инциденты",
  NORM: "Нормы",
  RULE: "Правила",
  USER: "Пользователи",
  SENSOR: "Датчики",
  HOUSE: "Птичники",
}

// Журнал действий пользователей (S2-06): кто и когда изменил инцидент, норму, партию.
export default function AuditPage() {
  const [entityType, setEntityType] = useState("")
  const entries = useApi<AuditEntry[]>(`/audit?limit=300${entityType ? `&entityType=${entityType}` : ""}`)

  return (
    <AppShell title="Журнал действий">
      <Panel>
        <div className="mb-3 flex flex-wrap gap-1.5">
          {Object.entries(TYPES).map(([value, label]) => (
            <button
              key={value}
              type="button"
              onClick={() => setEntityType(value)}
              className={`rounded-full px-3 py-1 text-sm ${entityType === value ? "bg-zinc-900 text-white dark:bg-white dark:text-zinc-900" : "bg-black/5 dark:bg-white/10"}`}
            >
              {label}
            </button>
          ))}
        </div>
        <ErrorNote message={entries.error} />
        <AuditList entries={entries.data ?? []} />
      </Panel>
    </AppShell>
  )
}
