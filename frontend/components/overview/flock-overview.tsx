"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { AlertTriangle, CheckCircle2, CircleAlert, RefreshCw } from "lucide-react"
import { ErrorNote, Stat } from "@/components/app/app-shell"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useApi } from "@/hooks/use-api"
import { api } from "@/lib/api"
import { cn } from "@/lib/utils"
import {
  formatDateTime,
  formatNumber,
  isOpenIncident,
  type Flock,
  type FlockKpi,
  type House,
  type IncidentSummary,
  type Norm,
  type NormMetric,
  type Sensor,
  type SensorReading,
} from "@/lib/types"

/** Обновление главной: показания и инциденты меняются раз в минуту (цикл движка правил). */
const REFRESH_MS = 60_000
const HOUSE_KEY = "overview.houseId"

type CategoryStatus = "critical" | "warning" | "ok"

/** Категории главной и типы инцидентов движка правил (docs/sprint2/08-rules-engine-spec.md п. 7). */
export const OVERVIEW_CATEGORIES: { id: string; label: string; matches: (type: string) => boolean }[] = [
  { id: "microclimate", label: "Микроклимат", matches: (type) => type === "MICROCLIMATE" || type === "SANITATION" },
  { id: "lighting", label: "Освещение", matches: (type) => type.startsWith("LIGHTING_") },
  { id: "herd", label: "Стадо", matches: (type) => type === "FLOCK_HEALTH" },
  { id: "resources", label: "Корм и вода", matches: (type) => type === "FEEDING" || type === "WATER_SUPPLY" },
  { id: "production", label: "Производство", matches: (type) => type === "PRODUCTION_METRICS" },
  { id: "equipment", label: "Датчики", matches: (type) => type === "SENSOR_NO_DATA" || type === "OTHER" },
]

/** Красная — есть открытый CRITICAL, жёлтая — открытый HIGH или MEDIUM, иначе зелёная. */
export function categoryStatus(incidents: IncidentSummary[]): CategoryStatus {
  const open = incidents.filter(isOpenIncident)
  if (open.some((incident) => incident.priority === "CRITICAL")) return "critical"
  if (open.some((incident) => incident.priority === "HIGH" || incident.priority === "MEDIUM")) return "warning"
  return "ok"
}

const SENSOR_METRICS: Record<string, { metric: NormMetric; label: string }> = {
  TEMPERATURE: { metric: "TEMPERATURE", label: "Температура" },
  HUMIDITY: { metric: "HUMIDITY", label: "Влажность" },
  CO2: { metric: "CO2", label: "CO2" },
  AMMONIA: { metric: "AMMONIA", label: "Аммиак" },
  LIGHT: { metric: "LIGHT_INTENSITY", label: "Освещённость" },
}

const STATUS_STYLES: Record<CategoryStatus, { box: string; text: string; label: string }> = {
  critical: {
    box: "border-red-300 bg-red-50 dark:border-red-900/60 dark:bg-red-950/40",
    text: "text-red-700 dark:text-red-300",
    label: "Критично",
  },
  warning: {
    box: "border-amber-300 bg-amber-50 dark:border-amber-900/60 dark:bg-amber-950/40",
    text: "text-amber-700 dark:text-amber-300",
    label: "Внимание",
  },
  ok: {
    box: "border-emerald-200 bg-emerald-50 dark:border-emerald-900/50 dark:bg-emerald-950/30",
    text: "text-emerald-700 dark:text-emerald-300",
    label: "В норме",
  },
}

const PRIORITY_LABELS: Record<string, string> = { CRITICAL: "Критичный", HIGH: "Высокий", MEDIUM: "Средний", LOW: "Низкий" }

function unitLabel(unit: string | null | undefined) {
  if (!unit) return ""
  if (unit === "C") return "°C"
  return unit === "lux" ? "лк" : unit
}

function belongsToHouse(incident: IncidentSummary, house: House) {
  return incident.houseId ? incident.houseId === house.id : incident.house === house.name
}

function readStoredHouse(): string | undefined {
  try {
    return window.localStorage.getItem(HOUSE_KEY) ?? undefined
  } catch {
    return undefined
  }
}

export function FlockOverview({ onOpenIncident }: { onOpenIncident: (incidentId: string) => void }) {
  const houses = useApi<House[]>("/houses")
  const sensors = useApi<Sensor[]>("/sensors")
  const incidents = useApi<IncidentSummary[]>("/incidents")
  const [chosenHouseId, setChosenHouseId] = useState<string | undefined>(readStoredHouseSafe)
  const [updatedAt, setUpdatedAt] = useState<Date>()

  const activeHouses = useMemo(() => (houses.data ?? []).filter((house) => house.active), [houses.data])
  const house =
    activeHouses.find((item) => item.id === chosenHouseId) ??
    activeHouses.find((item) => item.activeFlockId) ??
    activeHouses[0]

  const flock = useApi<Flock>(house?.activeFlockId ? `/flocks/${house.activeFlockId}` : null)
  const kpi = useApi<FlockKpi>(house?.activeFlockId ? `/flocks/${house.activeFlockId}/kpi` : null)
  const flockData = house?.activeFlockId && flock.data?.id === house.activeFlockId ? flock.data : undefined
  const kpiData = house?.activeFlockId && kpi.data?.flockId === house.activeFlockId ? kpi.data : undefined

  const { reload: reloadIncidents } = incidents
  const { reload: reloadKpi } = kpi
  useEffect(() => {
    const timer = window.setInterval(() => {
      reloadIncidents()
      reloadKpi()
      setUpdatedAt(new Date())
    }, REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [reloadIncidents, reloadKpi])

  const chooseHouse = (id: string) => {
    setChosenHouseId(id)
    try {
      window.localStorage.setItem(HOUSE_KEY, id)
    } catch {
      // хранилище недоступно — выбор живёт до перезагрузки
    }
  }

  const houseIncidents = useMemo(
    () => (house ? (incidents.data ?? []).filter((incident) => belongsToHouse(incident, house)) : []),
    [incidents.data, house],
  )
  const openIncidents = houseIncidents.filter(isOpenIncident)
  const houseSensors = useMemo(
    () => (sensors.data ?? []).filter((sensor) => sensor.houseId === house?.id && sensor.active),
    [sensors.data, house?.id],
  )

  if (houses.error) {
    return <ErrorNote message={`Нет связи с сервером: ${houses.error}`} />
  }
  if (houses.loading) {
    return <div className="dashboard-panel p-6 text-sm text-zinc-500">Загружаем птичники…</div>
  }
  if (!house) {
    return (
      <div className="dashboard-panel p-6 text-sm">
        <p className="font-medium">Птичники ещё не заведены.</p>
        <p className="mt-1 text-zinc-500">
          Администратор заводит площадку, птичники и датчики в разделе{" "}
          <Link className="text-[var(--primary)] hover:underline" href="/admin/structure">
            «Структура»
          </Link>
          .
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="dashboard-panel flex flex-wrap items-center gap-3 px-4 py-3">
        <Select value={house.id} onValueChange={chooseHouse}>
          <SelectTrigger className="w-[240px]" aria-label="Птичник">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {activeHouses.map((item) => (
              <SelectItem key={item.id} value={item.id}>
                {item.name}
                {item.activeFlockId ? "" : " — без партии"}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {flockData ? (
          <div className="text-sm text-zinc-600 dark:text-zinc-300">
            Партия{" "}
            <Link href={`/flocks/${flockData.id}`} className="font-medium text-[var(--primary)] hover:underline">
              {flockData.code}
            </Link>{" "}
            · {flockData.breedName} · день {flockData.ageDays ?? "—"}
            {flockData.targetAgeDays ? ` из ${flockData.targetAgeDays}` : ""}
          </div>
        ) : (
          <div className="text-sm text-zinc-500">В птичнике нет активной партии</div>
        )}
        <div className="ml-auto flex items-center gap-2 text-xs text-zinc-500">
          <span>обновляется раз в минуту</span>
          {updatedAt && <span>· {updatedAt.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" })}</span>}
          <Button
            variant="ghost"
            size="icon"
            aria-label="Обновить"
            onClick={() => {
              reloadIncidents()
              reloadKpi()
              sensors.reload()
              setUpdatedAt(new Date())
            }}
          >
            <RefreshCw className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <ErrorNote message={incidents.error && `Инциденты не загружены: ${incidents.error}`} />

      <div className="grid gap-3 sm:grid-cols-3 xl:grid-cols-6">
        {OVERVIEW_CATEGORIES.map((category) => {
          const inCategory = houseIncidents.filter((incident) => category.matches(incident.type))
          const status = categoryStatus(inCategory)
          const open = inCategory.filter(isOpenIncident).length
          const style = STATUS_STYLES[status]
          const Icon = status === "critical" ? CircleAlert : status === "warning" ? AlertTriangle : CheckCircle2
          return (
            <div key={category.id} data-testid={`category-${category.id}`} data-status={status} className={cn("rounded-2xl border px-4 py-3", style.box)}>
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-medium text-zinc-800 dark:text-zinc-100">{category.label}</span>
                <Icon className={cn("h-4 w-4", style.text)} />
              </div>
              <div className={cn("mt-1 text-sm font-semibold", style.text)}>{style.label}</div>
              <div className="text-xs text-zinc-500">{open ? `открытых инцидентов: ${open}` : "открытых инцидентов нет"}</div>
            </div>
          )
        })}
      </div>

      {flockData && <KpiCards kpi={kpiData} error={kpi.error} />}

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_420px]">
        <SensorPanel sensors={houseSensors} house={house} flock={flockData} />
        <section className="dashboard-panel p-4">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-base font-semibold">Открытые инциденты</h2>
            <span className="text-xs text-zinc-500">{openIncidents.length}</span>
          </div>
          {openIncidents.length === 0 ? (
            <p className="text-sm text-zinc-500">Открытых инцидентов по птичнику нет.</p>
          ) : (
            <ul className="space-y-2">
              {openIncidents.slice(0, 8).map((incident) => (
                <li key={incident.id}>
                  <button
                    type="button"
                    onClick={() => onOpenIncident(incident.id)}
                    className="w-full rounded-xl border border-zinc-200 px-3 py-2 text-left text-sm hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-900"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-medium">{incident.title}</span>
                      <span
                        className={cn(
                          "shrink-0 rounded-full px-2 py-0.5 text-xs",
                          incident.priority === "CRITICAL"
                            ? "bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300"
                            : "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
                        )}
                      >
                        {PRIORITY_LABELS[incident.priority] ?? incident.priority}
                      </span>
                    </div>
                    {incident.description && <p className="mt-0.5 line-clamp-2 text-xs text-zinc-500">{incident.description}</p>}
                    <p className="mt-0.5 text-xs text-zinc-400">
                      {incident.code} · {formatDateTime(incident.createdAt)}
                    </p>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}

function readStoredHouseSafe() {
  return typeof window === "undefined" ? undefined : readStoredHouse()
}

function deviation(value: number | null, suffix: string, digits: number, inverse = false) {
  if (value === null) return undefined
  const sign = value > 0 ? "+" : ""
  const good = inverse ? value <= 0 : value >= 0
  return <span className={good ? "text-emerald-600" : "text-red-600"}>{`${sign}${formatNumber(value, digits)}${suffix} к норме`}</span>
}

function KpiCards({ kpi, error }: { kpi?: FlockKpi; error?: string }) {
  if (error) return <ErrorNote message={`KPI не загружены: ${error}`} />
  if (!kpi) return <div className="dashboard-panel p-4 text-sm text-zinc-500">Считаем KPI…</div>
  const weightHint = kpi.weightAgeDays !== null ? `на день ${kpi.weightAgeDays}` : "нет взвешиваний"
  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
      <Stat label="Поголовье" value={formatNumber(kpi.heads)} hint={`потери ${formatNumber(kpi.lossPct, 2)}%`} />
      <Stat label="Сохранность" value={`${formatNumber(kpi.survivalPct, 2)}%`} hint={`падёж ${formatNumber(kpi.mortality)} · выбраковка ${formatNumber(kpi.culled)}`} />
      <Stat
        label="Средний вес"
        value={kpi.avgWeightG !== null ? `${formatNumber(kpi.avgWeightG)} г` : "—"}
        hint={kpi.weightDeviationPct !== null ? deviation(kpi.weightDeviationPct, "%", 1) : weightHint}
      />
      <Stat
        label="FCR"
        value={kpi.fcr !== null ? formatNumber(kpi.fcr, 3) : "—"}
        hint={
          kpi.fcrDeviation !== null
            ? deviation(kpi.fcrDeviation, "", 3, true)
            : kpi.fcr === null
              ? kpi.avgWeightG === null
                ? "нет взвешиваний"
                : "нет данных по корму"
              : undefined
        }
      />
      <Stat label="Привес" value={kpi.adgG !== null ? `${formatNumber(kpi.adgG, 1)} г/сут` : "—"} hint={weightHint} />
      <Stat
        label="EPEF"
        value={kpi.epef !== null ? formatNumber(kpi.epef) : "—"}
        hint={kpi.feedMissingDays > 0 ? `нет корма за ${kpi.feedMissingDays} сут — FCR неполный` : "норма 350–450"}
      />
    </div>
  )
}

type SensorRow = { sensor: Sensor; reading?: SensorReading; norm?: Norm | null }

function SensorPanel({ sensors, house, flock }: { sensors: Sensor[]; house: House; flock?: Flock }) {
  const [rows, setRows] = useState<SensorRow[]>([])
  const [error, setError] = useState<string>()
  const [version, setVersion] = useState(0)
  const zoneNames = useMemo(() => new Map(house.zones.map((zone) => [zone.id, zone.name])), [house.zones])
  const sensorKey = sensors.map((sensor) => sensor.id).join(",")

  useEffect(() => {
    const timer = window.setInterval(() => setVersion((value) => value + 1), REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    let cancelled = false
    const shown = sensors.filter((sensor) => SENSOR_METRICS[sensor.type])
    const metrics = [...new Set(shown.map((sensor) => SENSOR_METRICS[sensor.type].metric))]
    const normRequests = flock && flock.ageDays !== null
      ? metrics.map((metric) =>
          api<Norm | undefined>(`/norms/lookup?metric=${metric}&breedCode=${flock.breedCode}&ageDay=${flock.ageDays}`)
            .then((norm) => [metric, norm ?? null] as const)
            .catch(() => [metric, null] as const),
        )
      : []
    Promise.all([
      Promise.all(
        shown.map((sensor) =>
          api<SensorReading[]>(`/telemetry/readings?sensorCode=${encodeURIComponent(sensor.code)}&limit=1`)
            .then((readings) => ({ sensor, reading: readings[0] }))
            .catch(() => ({ sensor, reading: undefined })),
        ),
      ),
      Promise.all(normRequests),
    ])
      .then(([readings, norms]) => {
        if (cancelled) return
        const byMetric = new Map(norms)
        setRows(readings.map((row) => ({ ...row, norm: byMetric.get(SENSOR_METRICS[row.sensor.type].metric) })))
        setError(undefined)
      })
      .catch((reason: Error) => {
        if (!cancelled) setError(reason.message)
      })
    return () => {
      cancelled = true
    }
    // sensorKey — стабильный ключ списка датчиков вместо массива
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sensorKey, flock?.id, flock?.ageDays, version])

  return (
    <section className="dashboard-panel p-4">
      <h2 className="mb-3 text-base font-semibold">Показания датчиков</h2>
      <ErrorNote message={error} />
      {sensors.length === 0 ? (
        <p className="text-sm text-zinc-500">К птичнику не привязано ни одного датчика.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-xs uppercase tracking-wide text-zinc-500">
              <tr>
                <th className="py-1.5 pr-3 font-medium">Показатель</th>
                <th className="py-1.5 pr-3 font-medium">Зона / датчик</th>
                <th className="py-1.5 pr-3 text-right font-medium">Значение</th>
                <th className="py-1.5 pr-3 font-medium">Норма</th>
                <th className="py-1.5 font-medium">Время</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(({ sensor, reading, norm }) => {
                const unit = unitLabel(reading?.unit ?? sensor.unit)
                const outOfNorm =
                  reading && norm &&
                  ((norm.minValue !== null && reading.value < norm.minValue) || (norm.maxValue !== null && reading.value > norm.maxValue))
                const stale = reading && Date.now() - new Date(reading.measuredAt).getTime() > 30 * 60_000
                return (
                  <tr key={sensor.id} className="border-t border-zinc-100 dark:border-zinc-800">
                    <td className="py-1.5 pr-3">{SENSOR_METRICS[sensor.type]?.label ?? sensor.type}</td>
                    <td className="py-1.5 pr-3 text-zinc-500">
                      {sensor.zoneId ? zoneNames.get(sensor.zoneId) ?? "—" : "—"} · {sensor.code}
                    </td>
                    <td className={cn("py-1.5 pr-3 text-right font-medium tabular-nums", outOfNorm && "text-red-600")}>
                      {reading ? `${formatNumber(reading.value, 1)} ${unit}` : "нет данных"}
                    </td>
                    <td className="py-1.5 pr-3 text-zinc-500">{norm ? normText(norm, unit) : "—"}</td>
                    <td className={cn("py-1.5 text-xs", stale ? "text-amber-600" : "text-zinc-500")}>
                      {reading ? formatDateTime(reading.measuredAt) : "—"}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function normText(norm: Norm, unit: string) {
  if (norm.minValue !== null && norm.maxValue !== null) return `${formatNumber(norm.minValue, 1)}–${formatNumber(norm.maxValue, 1)} ${unit}`
  if (norm.maxValue !== null) return `≤ ${formatNumber(norm.maxValue, 1)} ${unit}`
  if (norm.minValue !== null) return `≥ ${formatNumber(norm.minValue, 1)} ${unit}`
  return "—"
}
