"use client"

import Link from "next/link"
import { useState } from "react"
import { Plus } from "lucide-react"
import { AppShell, ErrorNote, Panel } from "@/components/app/app-shell"
import { FlockFormDialog } from "@/components/flocks/flock-form-dialog"
import { FlockStatusBadge } from "@/components/flocks/flock-status-badge"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { useApi } from "@/hooks/use-api"
import { useCurrentUser } from "@/hooks/use-current-user"
import { formatDate, formatNumber, type Breed, type Flock, type FlockStatus, type House } from "@/lib/types"

export default function FlocksPage() {
  const user = useCurrentUser()
  const [status, setStatus] = useState<FlockStatus | "">("ACTIVE")
  const [houseId, setHouseId] = useState("")
  const [creating, setCreating] = useState(false)

  const query = new URLSearchParams()
  if (status) query.set("status", status)
  if (houseId) query.set("houseId", houseId)
  const flocks = useApi<Flock[]>(`/flocks?${query}`)
  const houses = useApi<House[]>("/houses")
  const breeds = useApi<Breed[]>("/breeds")
  const canPlace = user?.role === "TECHNOLOGIST" || user?.role === "ADMIN"

  return (
    <AppShell
      title="Партии"
      actions={
        canPlace && houses.data && breeds.data ? (
          <Button onClick={() => setCreating(true)}>
            <Plus className="mr-1 size-4" /> Посадить партию
          </Button>
        ) : null
      }
    >
      <Panel>
        <div className="mb-3 flex flex-wrap gap-2">
          <select className="h-9 rounded-lg border border-black/10 bg-white px-3 text-sm dark:border-white/10 dark:bg-white/5" value={status} onChange={(event) => setStatus(event.target.value as FlockStatus | "")}>
            <option value="ACTIVE">Активные</option>
            <option value="PLANNED">Запланированные</option>
            <option value="CLOSED">Закрытые</option>
            <option value="">Все</option>
          </select>
          <select className="h-9 rounded-lg border border-black/10 bg-white px-3 text-sm dark:border-white/10 dark:bg-white/5" value={houseId} onChange={(event) => setHouseId(event.target.value)}>
            <option value="">Все птичники</option>
            {houses.data?.map((house) => (
              <option key={house.id} value={house.id}>
                {house.name}
              </option>
            ))}
          </select>
        </div>
        <ErrorNote message={flocks.error} />
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Партия</TableHead>
              <TableHead>Птичник</TableHead>
              <TableHead>Кросс</TableHead>
              <TableHead>Посадка</TableHead>
              <TableHead className="text-right">Возраст, дн</TableHead>
              <TableHead className="text-right">Посажено</TableHead>
              <TableHead className="text-right">Поголовье</TableHead>
              <TableHead className="text-right">Падёж + брак</TableHead>
              <TableHead>Статус</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {flocks.data?.map((flock) => (
              <TableRow key={flock.id}>
                <TableCell>
                  <Link className="font-medium text-[var(--primary)] hover:underline" href={`/flocks/${flock.id}`}>
                    {flock.code}
                  </Link>
                </TableCell>
                <TableCell>{flock.houseName}</TableCell>
                <TableCell>{flock.breedName}</TableCell>
                <TableCell>{formatDate(flock.placedAt)}</TableCell>
                <TableCell className="text-right">{flock.ageDays ?? "—"}</TableCell>
                <TableCell className="text-right">{formatNumber(flock.placedHeads)}</TableCell>
                <TableCell className="text-right">{formatNumber(flock.currentHeads)}</TableCell>
                <TableCell className="text-right">{formatNumber(flock.mortalityTotal + flock.culledTotal)}</TableCell>
                <TableCell>
                  <FlockStatusBadge status={flock.status} />
                </TableCell>
              </TableRow>
            ))}
            {flocks.data?.length === 0 && (
              <TableRow>
                <TableCell colSpan={9} className="py-8 text-center text-zinc-500">
                  Партий нет
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Panel>

      {creating && houses.data && breeds.data && (
        <FlockFormDialog
          open={creating}
          onOpenChange={setCreating}
          houses={houses.data}
          breeds={breeds.data}
          onSaved={(flock) => {
            window.location.href = `/flocks/${flock.id}`
          }}
        />
      )}
    </AppShell>
  )
}
