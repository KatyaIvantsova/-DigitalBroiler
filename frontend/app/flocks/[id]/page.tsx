"use client"

import { use, useState } from "react"
import Link from "next/link"
import { AppShell, ErrorNote, Panel, Stat } from "@/components/app/app-shell"
import { AuditList } from "@/components/app/audit-list"
import { CloseFlockDialog } from "@/components/flocks/close-flock-dialog"
import { DailyJournal } from "@/components/flocks/daily-journal"
import { FlockFormDialog } from "@/components/flocks/flock-form-dialog"
import { FlockStatusBadge } from "@/components/flocks/flock-status-badge"
import { ImportRecordsDialog } from "@/components/flocks/import-records-dialog"
import { KpiPlanFact } from "@/components/flocks/kpi-plan-fact"
import { WeighingsPanel } from "@/components/flocks/weighings-panel"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useApi } from "@/hooks/use-api"
import { useCurrentUser } from "@/hooks/use-current-user"
import {
  SEX_LABELS,
  formatDate,
  formatNumber,
  type AuditEntry,
  type Breed,
  type DailyRecord,
  type Flock,
  type FlockKpi,
  type House,
  type PlanFact,
  type Weighing,
} from "@/lib/types"

export default function FlockPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const user = useCurrentUser()
  const flock = useApi<Flock>(`/flocks/${id}`)
  const records = useApi<DailyRecord[]>(`/flocks/${id}/daily-records`)
  const weighings = useApi<Weighing[]>(`/flocks/${id}/weighings`)
  const history = useApi<AuditEntry[]>(`/flocks/${id}/history`)
  const houses = useApi<House[]>("/houses")
  const breeds = useApi<Breed[]>("/breeds")
  const kpi = useApi<FlockKpi>(`/flocks/${id}/kpi`)
  const planFact = useApi<PlanFact>(`/flocks/${id}/plan-fact`)
  const [editing, setEditing] = useState(false)
  const [importing, setImporting] = useState(false)
  const [closing, setClosing] = useState(false)

  const reloadAll = () => {
    flock.reload()
    records.reload()
    weighings.reload()
    history.reload()
    kpi.reload()
    planFact.reload()
  }

  const data = flock.data
  const role = user?.role
  const isManagerOfFlocks = role === "TECHNOLOGIST" || role === "ADMIN"
  const canEnterJournal =
    data !== undefined &&
    data.status !== "PLANNED" &&
    (data.status === "ACTIVE" ? role !== undefined && role !== "MANAGER" : isManagerOfFlocks)
  const zones = houses.data?.find((house) => house.id === data?.houseId)?.zones ?? []
  const lastWeighing = weighings.data?.at(-1)
  const losses = data ? data.mortalityTotal + data.culledTotal : 0

  return (
    <AppShell
      title={data ? `Партия ${data.code}` : "Партия"}
      actions={
        data && isManagerOfFlocks ? (
          <div className="flex gap-2">
            {data.status !== "PLANNED" && (
              <Button variant="outline" onClick={() => setImporting(true)}>
                Импорт учёта
              </Button>
            )}
            <Button variant="outline" onClick={() => setEditing(true)} disabled={!houses.data || !breeds.data}>
              Изменить
            </Button>
            {data.status === "ACTIVE" && <Button onClick={() => setClosing(true)}>Закрыть партию</Button>}
          </div>
        ) : null
      }
    >
      <ErrorNote message={flock.error} />
      {data && (
        <>
          <div className="flex flex-wrap items-center gap-2 text-sm text-zinc-600 dark:text-zinc-300">
            <FlockStatusBadge status={data.status} />
            <span>{data.houseName}</span>·<span>{data.breedName}</span>·<span>{SEX_LABELS[data.sex]}</span>·
            <span>посадка {formatDate(data.placedAt)}</span>
            {data.closedAt && <span>· закрыта {formatDate(data.closedAt)}</span>}
            {data.hatchery && <span>· {data.hatchery}</span>}
            <Link href="/flocks" className="ml-auto text-[var(--primary)] hover:underline">
              ← Все партии
            </Link>
          </div>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
            <Stat
              label={data.status === "CLOSED" ? "Длительность цикла" : "Возраст"}
              value={data.ageDays !== null ? `${data.ageDays} дн` : "—"}
              hint={data.targetAgeDays ? `план ${data.targetAgeDays} дн` : undefined}
            />
            <Stat label="Посажено" value={formatNumber(data.placedHeads)} hint={data.placedAvgWeightG ? `${data.placedAvgWeightG} г` : undefined} />
            <Stat label="Поголовье" value={formatNumber(data.currentHeads)} hint={`сохранность ${formatNumber((data.currentHeads / data.placedHeads) * 100, 2)}%`} />
            <Stat label="Падёж / выбраковка" value={`${formatNumber(data.mortalityTotal)} / ${formatNumber(data.culledTotal)}`} hint={`потери ${formatNumber((losses / data.placedHeads) * 100, 2)}%`} />
            <Stat
              label="Последнее взвешивание"
              value={lastWeighing ? `${formatNumber(lastWeighing.avgWeightG)} г` : "—"}
              hint={lastWeighing ? `день ${lastWeighing.ageDay}` : undefined}
            />
          </div>
          {data.status === "CLOSED" && (
            <Panel>
              <p className="text-sm">
                Сдано {formatNumber(data.shippedHeads)} гол, {formatNumber(data.shippedLiveWeightKg, 1)} кг живого веса
                {data.shippedHeads ? ` (средний вес ${formatNumber(((data.shippedLiveWeightKg ?? 0) / data.shippedHeads) * 1000)} г)` : ""}.
              </p>
            </Panel>
          )}

          <Panel>
            <Tabs defaultValue="journal">
              <TabsList>
                <TabsTrigger value="journal">Учёт по дням</TabsTrigger>
                <TabsTrigger value="weighings">Взвешивания</TabsTrigger>
                <TabsTrigger value="kpi">KPI и план-факт</TabsTrigger>
                <TabsTrigger value="history">История изменений</TabsTrigger>
              </TabsList>
              <TabsContent value="journal" className="pt-3">
                <ErrorNote message={records.error} />
                <DailyJournal flock={data} records={records.data ?? []} canEdit={canEnterJournal} onChanged={reloadAll} />
              </TabsContent>
              <TabsContent value="weighings" className="pt-3">
                <ErrorNote message={weighings.error} />
                <WeighingsPanel flock={data} weighings={weighings.data ?? []} zones={zones} canEdit={canEnterJournal} onChanged={reloadAll} />
              </TabsContent>
              <TabsContent value="kpi" className="pt-3">
                <KpiPlanFact kpi={kpi.data} planFact={planFact.data} error={kpi.error ?? planFact.error} />
              </TabsContent>
              <TabsContent value="history" className="pt-3">
                <ErrorNote message={history.error} />
                <AuditList entries={history.data ?? []} />
              </TabsContent>
            </Tabs>
          </Panel>

          {editing && houses.data && breeds.data && (
            <FlockFormDialog open={editing} onOpenChange={setEditing} flock={data} houses={houses.data} breeds={breeds.data} onSaved={reloadAll} />
          )}
          {importing && <ImportRecordsDialog flock={data} onClose={() => setImporting(false)} onImported={reloadAll} />}
          {closing && <CloseFlockDialog flock={data} open={closing} onOpenChange={setClosing} onClosed={reloadAll} />}
        </>
      )}
    </AppShell>
  )
}
