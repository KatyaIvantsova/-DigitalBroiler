"use client"

import { useState } from "react"
import { Download, History, Pencil, Play, Upload } from "lucide-react"
import { AppShell, ErrorNote, Panel } from "@/components/app/app-shell"
import {
  EditNormDialog,
  EditRuleDialog,
  ImportNormsDialog,
  isRecordRule,
  NormHistoryDialog,
  normRange,
  recordRuleUnit,
  unitLabel,
} from "@/components/norms/norm-dialogs"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { useApi } from "@/hooks/use-api"
import { useCurrentUser } from "@/hooks/use-current-user"
import { api } from "@/lib/api"
import { NORM_METRIC_LABELS, type Breed, type Norm, type NormMetric, type Rule } from "@/lib/types"

const selectClass = "h-9 rounded-lg border border-black/10 bg-white px-3 text-sm dark:border-white/10 dark:bg-white/5"

type Summary = { sensors: number; raised: number; cleared: number; skipped: number }

// Справочник норм по возрасту и кроссу и пороги движка правил (S3-01, S3-02, S3-03).
export default function NormsPage() {
  const user = useCurrentUser()
  const canEdit = user?.role === "TECHNOLOGIST" || user?.role === "ADMIN"
  const [metric, setMetric] = useState<NormMetric>("TEMPERATURE")
  const [breed, setBreed] = useState("ROSS_308")
  const [editing, setEditing] = useState<Norm>()
  const [historyOf, setHistoryOf] = useState<Norm>()
  const [importing, setImporting] = useState(false)
  const [editingRule, setEditingRule] = useState<Rule>()
  const [summary, setSummary] = useState<string>()
  const [evaluateError, setEvaluateError] = useState<string>()

  const norms = useApi<Norm[]>(`/norms?metric=${metric}${breed ? `&breedCode=${breed}` : ""}`)
  const breeds = useApi<Breed[]>("/breeds")
  const rules = useApi<Rule[]>("/rules")

  const evaluate = async () => {
    setEvaluateError(undefined)
    try {
      const result = await api<Summary>("/rules/evaluate", { method: "POST" })
      setSummary(`Проверено датчиков: ${result.sensors}; сработало правил: ${result.raised}; закрыто инцидентов: ${result.cleared}`)
    } catch (reason) {
      setEvaluateError(reason instanceof Error ? reason.message : String(reason))
    }
  }

  return (
    <AppShell
      title="Нормы и правила"
      actions={
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => (window.location.href = "/api/v1/norms/export")}>
            <Download className="mr-1 size-4" /> Выгрузить CSV
          </Button>
          {canEdit && (
            <Button onClick={() => setImporting(true)}>
              <Upload className="mr-1 size-4" /> Импорт
            </Button>
          )}
        </div>
      }
    >
      <div className="grid gap-4">
        <Panel title="Нормы по возрасту">
          <div className="mb-3 flex flex-wrap gap-2">
            <select className={selectClass} value={metric} onChange={(event) => setMetric(event.target.value as NormMetric)}>
              {Object.entries(NORM_METRIC_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
            <select className={selectClass} value={breed} onChange={(event) => setBreed(event.target.value)}>
              <option value="">Все кроссы</option>
              {breeds.data?.map((item) => (
                <option key={item.code} value={item.code}>
                  {item.name}
                </option>
              ))}
            </select>
          </div>
          <ErrorNote message={norms.error} />
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Дни</TableHead>
                <TableHead>Кросс</TableHead>
                <TableHead className="text-right">Норма</TableHead>
                <TableHead className="text-right">Цель</TableHead>
                <TableHead>Ед.</TableHead>
                <TableHead className="text-right">Версия</TableHead>
                <TableHead>Источник</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {norms.data?.map((norm) => (
                <TableRow key={norm.id}>
                  <TableCell className="whitespace-nowrap">{norm.ageFromDay === norm.ageToDay ? norm.ageFromDay : `${norm.ageFromDay}–${norm.ageToDay}`}</TableCell>
                  <TableCell>{norm.breedCode ?? "все"}</TableCell>
                  <TableCell className="text-right font-medium whitespace-nowrap">{normRange(norm)}</TableCell>
                  <TableCell className="text-right">{norm.targetValue?.toLocaleString("ru-RU") ?? "—"}</TableCell>
                  <TableCell>{unitLabel(norm.unit)}</TableCell>
                  <TableCell className="text-right">{norm.version}</TableCell>
                  <TableCell className="max-w-[280px] truncate text-xs text-zinc-500" title={norm.source}>
                    {norm.source}
                  </TableCell>
                  <TableCell className="whitespace-nowrap text-right">
                    <Button variant="ghost" size="sm" onClick={() => setHistoryOf(norm)} title="История версий">
                      <History className="size-4" />
                    </Button>
                    {canEdit && (
                      <Button variant="ghost" size="sm" onClick={() => setEditing(norm)} title="Изменить">
                        <Pencil className="size-4" />
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {norms.data?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} className="text-center text-sm text-zinc-500">
                    Норм нет
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Panel>

        <Panel
          title="Правила оповещения"
          actions={
            canEdit ? (
              <Button variant="outline" onClick={evaluate}>
                <Play className="mr-1 size-4" /> Проверить сейчас
              </Button>
            ) : null
          }
        >
          <ErrorNote message={rules.error ?? evaluateError} />
          {summary && <p className="mb-2 text-sm text-zinc-600 dark:text-zinc-300">{summary}</p>}
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Правило</TableHead>
                <TableHead>Показатель</TableHead>
                <TableHead className="text-right">Предупреждение</TableHead>
                <TableHead className="text-right">Критично</TableHead>
                <TableHead className="text-right">Закрытие</TableHead>
                <TableHead>Статус</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {rules.data?.map((rule) => (
                <TableRow key={rule.code}>
                  <TableCell>
                    <div className="font-medium">{rule.name}</div>
                    <div className="text-xs text-zinc-500">{rule.code}</div>
                  </TableCell>
                  <TableCell>{rule.metric ? NORM_METRIC_LABELS[rule.metric] : "нет данных от датчика"}</TableCell>
                  {isRecordRule(rule) ? (
                    <>
                      <TableCell className="text-right">{`> ${rule.warnDelta?.toLocaleString("ru-RU")} ${recordRuleUnit(rule)}`}</TableCell>
                      <TableCell className="text-right">
                        {rule.criticalDelta !== null ? `> ${rule.criticalDelta.toLocaleString("ru-RU")} ${recordRuleUnit(rule)}` : "—"}
                      </TableCell>
                      <TableCell className="text-right">после исправления учёта</TableCell>
                    </>
                  ) : (
                    <>
                      <TableCell className="text-right">{rule.warnMinutes ? `${rule.warnMinutes} мин` : "—"}</TableCell>
                      <TableCell className="text-right">
                        {rule.criticalDelta !== null ? `±${rule.criticalDelta.toLocaleString("ru-RU")} за ${rule.criticalMinutes} мин` : "—"}
                      </TableCell>
                      <TableCell className="text-right">{rule.clearMinutes ? `${rule.clearMinutes} мин` : "—"}</TableCell>
                    </>
                  )}
                  <TableCell>{rule.enabled ? "включено" : "выключено"}</TableCell>
                  <TableCell className="text-right">
                    {canEdit && (
                      <Button variant="ghost" size="sm" onClick={() => setEditingRule(rule)} title="Изменить">
                        <Pencil className="size-4" />
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <p className="mt-2 text-xs text-zinc-500">
            Нормы берутся для возраста и кросса активной партии птичника. Программа освещения проверяется раз в час по показаниям за сутки. Правила по учёту (падёж, корм, вода) — при каждом вводе и правке учёта.
          </p>
        </Panel>
      </div>

      {editing && <EditNormDialog norm={editing} onClose={() => setEditing(undefined)} onSaved={norms.reload} />}
      {historyOf && <NormHistoryDialog norm={historyOf} onClose={() => setHistoryOf(undefined)} />}
      {importing && <ImportNormsDialog onClose={() => setImporting(false)} onImported={norms.reload} />}
      {editingRule && <EditRuleDialog rule={editingRule} onClose={() => setEditingRule(undefined)} onSaved={rules.reload} />}
    </AppShell>
  )
}
