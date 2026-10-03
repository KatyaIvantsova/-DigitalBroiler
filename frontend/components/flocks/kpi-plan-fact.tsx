"use client"

import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"
import { ErrorNote } from "@/components/app/app-shell"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { cn } from "@/lib/utils"
import { formatDate, formatNumber, type FlockKpi, type PlanFact } from "@/lib/types"

const FACT_COLOR = "#2563eb"
const NORM_COLOR = "#a1a1aa"

/** Вкладка «KPI и план-факт» экрана партии (S4-04): показатели по S1-04 и графики против кривой кросса. */
export function KpiPlanFact({ kpi, planFact, error }: { kpi?: FlockKpi; planFact?: PlanFact; error?: string }) {
  if (error) return <ErrorNote message={error} />
  if (!kpi || !planFact) return <p className="text-sm text-zinc-500">Считаем KPI…</p>

  const factByDay = new Map(planFact.weighings.map((point) => [point.ageDay, point]))
  const series = planFact.curve.map((norm) => ({
    ageDay: norm.ageDay,
    normWeight: norm.weightG,
    normFcr: norm.fcr,
    weight: factByDay.get(norm.ageDay)?.weightG ?? null,
    fcr: factByDay.get(norm.ageDay)?.fcr ?? null,
  }))
  // Взвешивания позже конца кривой (передержка) тоже должны попасть на график
  planFact.weighings
    .filter((point) => !planFact.curve.some((norm) => norm.ageDay === point.ageDay))
    .forEach((point) => series.push({ ageDay: point.ageDay, normWeight: null, normFcr: null, weight: point.weightG, fcr: point.fcr }))
  series.sort((a, b) => a.ageDay - b.ageDay)

  return (
    <div className="space-y-4">
      <div className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2 xl:grid-cols-4">
        <KpiLine label="Сохранность" value={`${formatNumber(kpi.survivalPct, 2)} %`} />
        <KpiLine label="Потери (падёж + выбраковка)" value={`${formatNumber(kpi.lossPct, 2)} %`} />
        {kpi.unaccountedHeads !== null && <KpiLine label="Не учтено при сдаче" value={`${formatNumber(kpi.unaccountedHeads)} гол`} warn />}
        <KpiLine label="Живая масса" value={kpi.liveMassKg !== null ? `${formatNumber(kpi.liveMassKg)} кг` : "—"} />
        <KpiLine label="Корм" value={kpi.feedKg !== null ? `${formatNumber(kpi.feedKg)} кг` : "—"} warn={kpi.feedMissingDays > 0}
          hint={kpi.feedMissingDays > 0 ? `нет данных за ${kpi.feedMissingDays} сут` : undefined} />
        <KpiLine label="FCR" value={kpi.fcr !== null ? formatNumber(kpi.fcr, 3) : "—"}
          hint={kpi.normFcr !== null ? `норма ${formatNumber(kpi.normFcr, 3)}` : undefined} />
        <KpiLine label="Среднесуточный привес" value={kpi.adgG !== null ? `${formatNumber(kpi.adgG, 1)} г/сут` : "—"} />
        <KpiLine label="EPEF" value={kpi.epef !== null ? formatNumber(kpi.epef) : "—"} />
        <KpiLine
          label={kpi.closed ? "Средний вес при сдаче" : "Последний вес"}
          value={kpi.avgWeightG !== null ? `${formatNumber(kpi.avgWeightG)} г` : "—"}
          hint={kpi.weightDate ? `${formatDate(kpi.weightDate)}, день ${kpi.weightAgeDays}` : "нет взвешиваний"}
        />
      </div>
      <p className="text-xs text-zinc-500">
        {kpi.closed
          ? "FCR, привес и EPEF посчитаны на сдачу."
          : "FCR, привес и EPEF посчитаны на дату последнего взвешивания: корм и потери взяты по эту дату включительно."}
      </p>

      <div className="grid gap-4 xl:grid-cols-2">
        <Chart title="Живой вес, г" data={series} fact="weight" norm="normWeight" />
        <Chart title="FCR нарастающим итогом" data={series} fact="fcr" norm="normFcr" digits={3} />
      </div>

      {planFact.weighings.length === 0 ? (
        <p className="text-sm text-zinc-500">Взвешиваний ещё нет — план-факт появится после первого.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Дата</TableHead>
              <TableHead className="text-right">День</TableHead>
              <TableHead className="text-right">Вес, г</TableHead>
              <TableHead className="text-right">Норма, г</TableHead>
              <TableHead className="text-right">Отклонение</TableHead>
              <TableHead className="text-right">Однородность</TableHead>
              <TableHead className="text-right">FCR</TableHead>
              <TableHead className="text-right">Норма FCR</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {planFact.weighings.map((point) => (
              <TableRow key={`${point.date}-${point.weightG}`}>
                <TableCell>{formatDate(point.date)}</TableCell>
                <TableCell className="text-right">{point.ageDay}</TableCell>
                <TableCell className="text-right">{formatNumber(point.weightG)}</TableCell>
                <TableCell className="text-right">{formatNumber(point.normWeightG)}</TableCell>
                <TableCell
                  className={cn(
                    "text-right",
                    point.weightDeviationPct !== null && (point.weightDeviationPct < -5 ? "text-red-600" : "text-emerald-600"),
                  )}
                >
                  {point.weightDeviationG !== null
                    ? `${point.weightDeviationG > 0 ? "+" : ""}${formatNumber(point.weightDeviationG)} г (${formatNumber(point.weightDeviationPct, 1)} %)`
                    : "—"}
                </TableCell>
                <TableCell className="text-right">{point.uniformityPct !== null ? `${formatNumber(point.uniformityPct, 1)} %` : "—"}</TableCell>
                <TableCell className="text-right">{formatNumber(point.fcr, 3)}</TableCell>
                <TableCell className="text-right">{formatNumber(point.normFcr, 3)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  )
}

function KpiLine({ label, value, hint, warn }: { label: string; value: string; hint?: string; warn?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-3 border-b border-zinc-100 py-1 dark:border-zinc-800">
      <span className="text-zinc-500">{label}</span>
      <span className={cn("text-right font-medium tabular-nums", warn && "text-amber-600")}>
        {value}
        {hint && <span className="ml-1 text-xs font-normal text-zinc-500">({hint})</span>}
      </span>
    </div>
  )
}

type Point = { ageDay: number; weight: number | null; normWeight: number | null; fcr: number | null; normFcr: number | null }

function Chart({ title, data, fact, norm, digits = 0 }: { title: string; data: Point[]; fact: keyof Point; norm: keyof Point; digits?: number }) {
  const hasNorm = data.some((point) => point[norm] !== null)
  const hasFact = data.some((point) => point[fact] !== null)
  return (
    <div className="rounded-2xl border border-zinc-200 p-3 dark:border-zinc-800">
      <div className="mb-2 text-sm font-medium">{title}</div>
      {!hasNorm && !hasFact ? (
        <p className="py-10 text-center text-sm text-zinc-500">Нет данных</p>
      ) : (
        <ResponsiveContainer width="100%" height={240}>
          <LineChart data={data} margin={{ top: 4, right: 12, bottom: 4, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e4e4e7" />
            <XAxis dataKey="ageDay" tick={{ fontSize: 11 }} label={{ value: "день", position: "insideBottomRight", offset: -2, fontSize: 11 }} />
            <YAxis tick={{ fontSize: 11 }} width={48} />
            <Tooltip
              formatter={(value: number, name: string) => [formatNumber(value, digits), name]}
              labelFormatter={(day) => `День ${day}`}
            />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            <Line type="monotone" dataKey={norm} name="Норма кросса" stroke={NORM_COLOR} strokeDasharray="5 4" dot={false} connectNulls />
            <Line type="monotone" dataKey={fact} name="Факт" stroke={FACT_COLOR} strokeWidth={2} dot={{ r: 3 }} connectNulls />
          </LineChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
