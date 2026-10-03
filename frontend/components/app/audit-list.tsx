import { formatDateTime, type AuditEntry } from "@/lib/types"

// Лента журнала действий: кто, когда, что и какие поля изменились.
export function AuditList({ entries, emptyText = "Записей нет" }: { entries: AuditEntry[]; emptyText?: string }) {
  if (entries.length === 0) {
    return <p className="py-6 text-center text-sm text-zinc-500">{emptyText}</p>
  }
  return (
    <ol className="divide-y divide-black/5 dark:divide-white/8">
      {entries.map((entry) => (
        <li key={entry.id} className="py-2.5 text-sm">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <span className="font-medium text-zinc-900 dark:text-zinc-100">{entry.summary}</span>
            <span className="text-xs text-zinc-500">
              {formatDateTime(entry.createdAt)} · {entry.actorName ?? "Система"}
            </span>
          </div>
          {entry.changes && <pre className="mt-1 whitespace-pre-wrap font-sans text-xs text-zinc-600 dark:text-zinc-400">{entry.changes}</pre>}
        </li>
      ))}
    </ol>
  )
}
