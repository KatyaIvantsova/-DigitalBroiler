"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import {
  Activity,
  AlertCircle,
  BarChart3,
  CheckCircle2,
  Clock3,
  RefreshCcw,
  RotateCcw,
} from "lucide-react"

import { HouseAnalyticsTable } from "@/components/dashboard/house-analytics-table"
import { IncidentAnalyticsKpi } from "@/components/dashboard/incident-analytics-kpi"
import { IncidentBreakdownChart } from "@/components/dashboard/incident-breakdown-chart"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import {
  formatAnalyticsPeriod,
  formatDuration,
  formatPercent,
  type IncidentAnalyticsResponse,
  type IncidentRegistryFilters,
} from "@/lib/incident-analytics"
import { cn } from "@/lib/utils"

interface IncidentAnalyticsPageProps {
  onOpenRegistry: (filters: IncidentRegistryFilters) => void
}

const priorityColors: Record<string, string> = {
  CRITICAL: "bg-red-500",
  HIGH: "bg-orange-500",
  MEDIUM: "bg-amber-400",
  LOW: "bg-emerald-500",
}

const statusColors: Record<string, string> = {
  OPEN: "bg-sky-500",
  IN_PROGRESS: "bg-violet-500",
  RESOLVED: "bg-teal-500",
  CLOSED: "bg-emerald-500",
  CANCELLED: "bg-zinc-400",
}

const typeColors: Record<string, string> = {
  // Backend enum keys
  MICROCLIMATE: "bg-sky-500",
  SANITATION: "bg-emerald-500",
  FLOCK_HEALTH: "bg-violet-500",
  FEEDING: "bg-orange-500",
  WATER_SUPPLY: "bg-blue-500",
  PRODUCTION_METRICS: "bg-zinc-500",
  OTHER: "bg-zinc-400",

  // Все LIGHTING_* — один цвет (жёлтый/янтарный)
  LIGHTING_ILLUMINANCE_LOW: "bg-amber-400",
  LIGHTING_ILLUMINANCE_HIGH: "bg-amber-400",
  LIGHTING_UNIFORMITY_VIOLATION: "bg-amber-400",
  LIGHTING_SYSTEM_HEALTH_WARNING: "bg-amber-400",
  LIGHTING_SYSTEM_HEALTH_CRITICAL: "bg-amber-400",
  LIGHTING_SCHEDULE_DEVIATION: "bg-amber-400",
  LIGHTING_DARK_PERIOD_VIOLATION: "bg-amber-400",
  LIGHTING_CONTROLLER_FAILURE: "bg-amber-400",
  LIGHTING_CONTINUOUS_LIGHT: "bg-amber-400",
  LIGHTING_CONTINUOUS_DARK: "bg-amber-400",
  LIGHTING_MISSING_EVENTS: "bg-amber-400",
}

function AnalyticsSkeleton() {
  return (
    <div className="space-y-4 p-3 md:p-4" aria-label="Загрузка аналитики">
      <div className="dashboard-panel p-5">
        <Skeleton className="h-7 w-56" />
        <Skeleton className="mt-3 h-4 w-80 max-w-full" />
        <div className="mt-6 grid gap-3 md:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => <Skeleton key={index} className="h-16" />)}
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, index) => <Skeleton key={index} className="h-44 rounded-[24px]" />)}
      </div>
      <div className="grid gap-4 xl:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => <Skeleton key={index} className="h-80 rounded-[24px]" />)}
      </div>
      <Skeleton className="h-80 rounded-[24px]" />
    </div>
  )
}

export function IncidentAnalyticsPage({ onOpenRegistry }: IncidentAnalyticsPageProps) {
  const [periodDays, setPeriodDays] = useState<7 | 30>(7)
  const [workshop, setWorkshop] = useState("")
  const [house, setHouse] = useState("")
  const [data, setData] = useState<IncidentAnalyticsResponse | null>(null)
  const [error, setError] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null)
  const requestCounter = useRef(0)
  const hasData = useRef(false)

  const loadAnalytics = useCallback(async (signal?: AbortSignal) => {
    const requestId = ++requestCounter.current
    setError("")
    setIsLoading(!hasData.current)
    setIsRefreshing(hasData.current)

    const params = new URLSearchParams({
      periodDays: String(periodDays),
      slaMinutes: "30",
    })
    if (workshop) params.set("workshop", workshop)
    if (house) params.set("house", house)

    try {
      const response = await fetch(`/api/incidents/analytics?${params.toString()}`, {
        cache: "no-store",
        signal,
      })

      if (!response.ok) {
        const body = (await response.json().catch(() => null)) as { message?: string } | null
        throw new Error(body?.message || `Не удалось загрузить аналитику (ошибка ${response.status}).`)
      }

      const nextData = (await response.json()) as IncidentAnalyticsResponse
      if (requestId !== requestCounter.current) return

      setData(nextData)
      hasData.current = true
      setLastUpdatedAt(new Date())
    } catch (requestError) {
      if (signal?.aborted || requestId !== requestCounter.current) return
      console.error("Не удалось загрузить аналитику инцидентов", requestError)
      setError(
        requestError instanceof TypeError
          ? "Нет связи с сервером. Проверьте соединение и повторите запрос."
          : requestError instanceof Error
            ? requestError.message
            : "Не удалось загрузить аналитику.",
      )
    } finally {
      if (requestId === requestCounter.current) {
        setIsLoading(false)
        setIsRefreshing(false)
      }
    }
  }, [house, periodDays, workshop])

  useEffect(() => {
    const controller = new AbortController()
    void loadAnalytics(controller.signal)
    return () => controller.abort()
  }, [loadAnalytics])

  const workshopOptions = useMemo(
    () => Array.from(new Set(data?.byHouse.map((item) => item.workshop) ?? [])),
    [data],
  )
  const houseOptions = useMemo(
    () => Array.from(new Set(
      (data?.byHouse ?? [])
        .filter((item) => !workshop || item.workshop === workshop)
        .map((item) => item.house),
    )),
    [data, workshop],
  )
  const visibleHouseRows = useMemo(
    () => (data?.byHouse ?? [])
      .filter((item) => (!workshop || item.workshop === workshop) && (!house || item.house === house))
      .sort((a, b) => b.total - a.total || (a.slaPercent ?? Infinity) - (b.slaPercent ?? Infinity)),
    [data, house, workshop],
  )

  const baseRegistryFilters = useMemo<IncidentRegistryFilters>(() => ({
    periodDays,
    ...(workshop ? { workshop } : {}),
    ...(house ? { house } : {}),
  }), [house, periodDays, workshop])

  const resetFilters = () => {
    setPeriodDays(7)
    setWorkshop("")
    setHouse("")
  }

  if (isLoading && !data) return <AnalyticsSkeleton />

  if (error && !data) {
    return (
      <div className="p-3 md:p-4">
        <div className="dashboard-panel flex min-h-80 flex-col items-center justify-center p-8 text-center">
          <span className="grid size-12 place-items-center rounded-2xl bg-red-500/10 text-red-600 dark:text-red-400">
            <AlertCircle className="size-6" />
          </span>
          <h1 className="mt-4 text-xl font-semibold">Аналитика временно недоступна</h1>
          <p className="mt-2 max-w-md text-sm text-zinc-500 dark:text-zinc-400">{error}</p>
          <Button className="mt-5" onClick={() => void loadAnalytics()}>
            <RefreshCcw className="size-4" />Повторить
          </Button>
        </div>
      </div>
    )
  }

  if (!data) return null

  const hasIncidents = data.kpi.total > 0

  return (
    <main className="space-y-4 p-3 md:p-4">
      <section className="dashboard-panel p-5 md:p-6">
        <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-semibold tracking-tight text-zinc-950 dark:text-zinc-50">
                Аналитика инцидентов
              </h1>
              
              {isRefreshing && (
                <span className="dashboard-chip">
                  <RefreshCcw className="size-3.5 animate-spin" />Обновление
                </span>
              )}
            </div>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-zinc-500 dark:text-zinc-400">
              Контроль скорости реакции и соблюдения  структуры инцидентов по производственным площадкам.
            </p>
            <p className="mt-2 text-xs text-zinc-400 dark:text-zinc-500">
              Период данных: {formatAnalyticsPeriod(data.period)}
              {lastUpdatedAt && ` · обновлено ${lastUpdatedAt.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" })}`}
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={resetFilters} disabled={isRefreshing}>
              <RotateCcw className="size-4" />Сбросить
            </Button>
            <Button variant="outline" size="sm" onClick={() => void loadAnalytics()} disabled={isRefreshing}>
              <RefreshCcw className={cn("size-4", isRefreshing && "animate-spin")} />Обновить
            </Button>
          </div>
        </div>

        <div className="mt-6 grid gap-4 md:grid-cols-[auto_minmax(0,1fr)_minmax(0,1fr)]">
          <fieldset>
            <legend className="mb-1.5 text-xs uppercase tracking-wide text-zinc-500 dark:text-zinc-400">Период</legend>
            <div className="inline-flex h-10 rounded-lg border border-zinc-300 bg-white p-1 dark:border-white/10 dark:bg-white/5">
              {([7, 30] as const).map((days) => (
                <button
                  key={days}
                  type="button"
                  onClick={() => setPeriodDays(days)}
                  className={cn(
                    "rounded-md px-4 text-sm font-medium transition",
                    periodDays === days
                      ? "bg-zinc-950 text-white shadow-sm dark:bg-white dark:text-zinc-950"
                      : "text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white",
                  )}
                >
                  {days} дней
                </button>
              ))}
            </div>
          </fieldset>

          

          <label className="flex min-w-0 flex-col gap-1.5">
            <span className="text-xs uppercase tracking-wide text-zinc-500 dark:text-zinc-400">Птичник</span>
            <select
              value={house}
              onChange={(event) => setHouse(event.target.value)}
              className="h-10 rounded-lg border border-zinc-300 bg-white px-3 text-sm text-zinc-900 outline-none transition hover:bg-zinc-50 focus:border-zinc-500 disabled:opacity-60 dark:border-white/10 dark:bg-white/5 dark:text-zinc-100 dark:hover:bg-white/8"
            >
              <option value="">Все птичники</option>
              {houseOptions.map((option) => <option key={option}>{option}</option>)}
            </select>
          </label>
        </div>

        {error && (
          <div className="mt-4 flex items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-200">
            <span>{error} Показаны последние полученные данные.</span>
            <Button variant="outline" size="sm" onClick={() => void loadAnalytics()}>Повторить</Button>
          </div>
        )}
      </section>

      {!hasIncidents ? (
        <section className="dashboard-panel flex min-h-80 flex-col items-center justify-center p-8 text-center">
          <CheckCircle2 className="size-10 text-emerald-500" />
          <h2 className="mt-4 text-lg font-semibold">За выбранный период инцидентов нет</h2>
          <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">Измените период, цех или птичник.</p>
        </section>
      ) : (
        <>
          <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <IncidentAnalyticsKpi
              label="Всего инцидентов"
              value={String(data.kpi.total)}
              details={`Активных ${data.kpi.active} · критических ${data.kpi.critical}`}
              icon={BarChart3}
              tone="neutral"
              onClick={() => onOpenRegistry(baseRegistryFilters)}
            />
            <IncidentAnalyticsKpi
              label="Время реакции"
              value={formatDuration(data.kpi.avgReactionMinutes)}
              details={`Медиана ${formatDuration(data.kpi.medianReactionMinutes)} · измерено ${data.kpi.reactionMeasuredCount} из ${data.kpi.reactionEligibleCount}`}
              icon={Clock3}
              tone="sky"
              tooltip="Время реакции измеряется от создания инцидента до первого перехода в работу."
            />
            <IncidentAnalyticsKpi
              label="Реакция в SLA"
              value={formatPercent(data.kpi.slaPercent)}
              details={`${data.kpi.slaMetCount} в SLA · ${data.kpi.slaBreachedCount} нарушено · ${data.kpi.slaPendingCount} ожидают`}
              icon={Activity}
              tone="emerald"
              tooltip="«Реакция в SLA» показывает, в каком проценте случаев инцидент был взят в работу до истечения 30 минут." 
            />
            <IncidentAnalyticsKpi
              label="Время закрытия"
              value={formatDuration(data.kpi.avgCloseMinutes)}
              details={`Медиана ${formatDuration(data.kpi.medianCloseMinutes)} · измерено ${data.kpi.closeMeasuredCount}`}
              icon={CheckCircle2}
              tone="violet"
              tooltip="Время закрытия — это время от обнаружения инцидента до его окончательного закрытия. Считается только для завершенных инцидентов. Решённые, но не закрытые инциденты не учитываются."
            />
          </section>

          <section className="grid gap-4 xl:grid-cols-3">
            <IncidentBreakdownChart
              title="Типы инцидентов"
              description="По убыванию количества"
              data={data.byType}
              colors={typeColors}
              onSelect={(type) => onOpenRegistry({ ...baseRegistryFilters, type })}
            />
            <IncidentBreakdownChart
              title="Приоритеты"
              description="Распределение по уровню риска"
              data={data.byPriority}
              colors={priorityColors}
              variant="segments"
              onSelect={(priority) => onOpenRegistry({ ...baseRegistryFilters, priority })}
            />
            <IncidentBreakdownChart
              title="Статусы"
              description="Решённые и закрытые показаны отдельно"
              data={data.byStatus}
              colors={statusColors}
              variant="segments"
              onSelect={(status) => onOpenRegistry({ ...baseRegistryFilters, status })}
            />
          </section>

          {visibleHouseRows.length > 0 ? (
            <HouseAnalyticsTable
              rows={visibleHouseRows}
              onSelect={(selectedWorkshop, selectedHouse) => onOpenRegistry({
                ...baseRegistryFilters,
                workshop: selectedWorkshop,
                house: selectedHouse,
              })}
            />
          ) : (
            <section className="dashboard-panel p-8 text-center text-sm text-zinc-500 dark:text-zinc-400">
              Для выбранного цеха и птичника нет строк аналитики.
            </section>
          )}
        </>
      )}
    </main>
  )
}
