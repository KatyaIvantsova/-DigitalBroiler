import { FLOCK_STATUS_LABELS, type FlockStatus } from "@/lib/types"
import { cn } from "@/lib/utils"

const STYLES: Record<FlockStatus, string> = {
  PLANNED: "bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-200",
  ACTIVE: "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-200",
  CLOSED: "bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-200",
}

export function FlockStatusBadge({ status }: { status: FlockStatus }) {
  return <span className={cn("rounded-full px-2.5 py-0.5 text-xs font-medium", STYLES[status])}>{FLOCK_STATUS_LABELS[status]}</span>
}
