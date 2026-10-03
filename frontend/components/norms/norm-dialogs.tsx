"use client"

import { useState } from "react"
import { Field, ErrorNote, inputClass } from "@/components/app/app-shell"
import { FormDialog } from "@/components/app/form-dialog"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { useApi } from "@/hooks/use-api"
import { api } from "@/lib/api"
import { formatDateTime, NORM_METRIC_LABELS, type Norm, type Rule } from "@/lib/types"

const toNumber = (value: string) => (value.trim() === "" ? null : Number(value.replace(",", ".")))
const fromNumber = (value: number | null) => (value === null ? "" : String(value))

export function normRange(norm: Pick<Norm, "minValue" | "targetValue" | "maxValue">) {
  const fmt = (value: number) => value.toLocaleString("ru-RU", { maximumFractionDigits: 3 })
  if (norm.minValue !== null && norm.maxValue !== null) return `${fmt(norm.minValue)} – ${fmt(norm.maxValue)}`
  if (norm.maxValue !== null) return `≤ ${fmt(norm.maxValue)}`
  if (norm.minValue !== null) return `≥ ${fmt(norm.minValue)}`
  return norm.targetValue !== null ? `≈ ${fmt(norm.targetValue)}` : "—"
}

export const unitLabel = (unit: string) => (unit === "C" ? "°C" : unit)

export function describeNorm(norm: Norm) {
  const days = norm.ageFromDay === norm.ageToDay ? `день ${norm.ageFromDay}` : `дни ${norm.ageFromDay}–${norm.ageToDay}`
  return `${NORM_METRIC_LABELS[norm.metric]}, ${norm.breedCode ?? "все кроссы"}, ${days}`
}

// Правка нормы: сохраняет новую версию, старая остаётся в истории (S3-02).
export function EditNormDialog({ norm, onClose, onSaved }: { norm: Norm; onClose: () => void; onSaved: () => void }) {
  const [min, setMin] = useState(fromNumber(norm.minValue))
  const [target, setTarget] = useState(fromNumber(norm.targetValue))
  const [max, setMax] = useState(fromNumber(norm.maxValue))
  const [source, setSource] = useState(norm.source)
  const [comment, setComment] = useState("")

  return (
    <FormDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={describeNorm(norm)}
      submitLabel="Сохранить новую версию"
      onSubmit={async () => {
        await api(`/norms/${norm.id}`, {
          method: "PUT",
          json: { minValue: toNumber(min), targetValue: toNumber(target), maxValue: toNumber(max), source, comment },
        })
        onSaved()
      }}
    >
      <div className="grid grid-cols-3 gap-3">
        <Field label={`Минимум, ${unitLabel(norm.unit)}`}>
          <input className={inputClass} inputMode="decimal" value={min} onChange={(event) => setMin(event.target.value)} />
        </Field>
        <Field label={`Цель, ${unitLabel(norm.unit)}`}>
          <input className={inputClass} inputMode="decimal" value={target} onChange={(event) => setTarget(event.target.value)} />
        </Field>
        <Field label={`Максимум, ${unitLabel(norm.unit)}`}>
          <input className={inputClass} inputMode="decimal" value={max} onChange={(event) => setMax(event.target.value)} />
        </Field>
      </div>
      <Field label="Источник">
        <input className={inputClass} value={source} onChange={(event) => setSource(event.target.value)} />
      </Field>
      <Field label="Причина изменения" hint="Обязательно: попадёт в историю нормы и журнал действий">
        <input className={inputClass} required value={comment} onChange={(event) => setComment(event.target.value)} />
      </Field>
      <p className="text-xs text-zinc-500">Пустое поле — граница не задана. Движок правил применит новую норму со следующей минуты.</p>
    </FormDialog>
  )
}

export function NormHistoryDialog({ norm, onClose }: { norm: Norm; onClose: () => void }) {
  const history = useApi<Norm[]>(`/norms/${norm.id}/history`)
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>История: {describeNorm(norm)}</DialogTitle>
        </DialogHeader>
        <ErrorNote message={history.error} />
        <ol className="grid gap-2">
          {history.data?.map((version) => (
            <li key={version.id} className="rounded-xl border border-black/10 px-3 py-2 text-sm dark:border-white/10">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <span className="font-medium">
                  Версия {version.version}: {normRange(version)} {unitLabel(version.unit)}
                  {version.validTo === null && <span className="ml-2 rounded-full bg-emerald-100 px-2 py-0.5 text-xs text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200">действует</span>}
                </span>
                <span className="text-xs text-zinc-500">
                  {formatDateTime(version.validFrom)}
                  {version.validTo && ` — ${formatDateTime(version.validTo)}`}
                </span>
              </div>
              <div className="mt-1 text-xs text-zinc-500">
                {version.changedBy ?? "—"}
                {version.changeComment && ` · ${version.changeComment}`}
              </div>
              <div className="mt-0.5 text-xs text-zinc-500">Источник: {version.source}</div>
            </li>
          ))}
        </ol>
      </DialogContent>
    </Dialog>
  )
}

type ImportResult = { created: number; updated: number; unchanged: number; errors: string[] }

export function ImportNormsDialog({ onClose, onImported }: { onClose: () => void; onImported: () => void }) {
  const [file, setFile] = useState<File | null>(null)
  const [comment, setComment] = useState("Импорт таблицы норм")
  const [result, setResult] = useState<ImportResult>()

  return (
    <FormDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title="Импорт норм из CSV"
      submitLabel="Загрузить"
      onSubmit={async () => {
        if (!file) throw new Error("Выберите файл")
        const form = new FormData()
        form.append("file", file)
        form.append("comment", comment)
        const imported = await api<ImportResult>("/norms/import", { method: "POST", body: form })
        setResult(imported)
        onImported()
        if (imported.errors.length > 0) throw new Error(`Загружено с ошибками: ${imported.errors.join("; ")}`)
      }}
    >
      <p className="text-sm text-zinc-600 dark:text-zinc-300">
        Формат как у выгрузки: <code>breed;metric;age_from;age_to;min;target;max;unit;source</code>. Строка с тем же показателем, кроссом и днями создаёт новую версию нормы.
      </p>
      <Field label="Файл CSV">
        <input type="file" accept=".csv,text/csv" className="text-sm" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
      </Field>
      <Field label="Комментарий">
        <input className={inputClass} value={comment} onChange={(event) => setComment(event.target.value)} />
      </Field>
      {result && (
        <p className="text-sm">
          Новых: {result.created}, изменено: {result.updated}, без изменений: {result.unchanged}
        </p>
      )}
    </FormDialog>
  )
}

/** Правила по ежедневному учёту (S4-05): пороги — кратность нормы падежа или падение расхода к прошлым суткам, %. */
export function isRecordRule(rule: Pick<Rule, "code" | "sensorType" | "warnDelta">) {
  return rule.sensorType === null && rule.warnDelta !== null
}

export function recordRuleUnit(rule: Pick<Rule, "code">) {
  return rule.code === "FLOCK_MORTALITY_DAILY" ? "× нормы" : "%"
}

export function EditRuleDialog({ rule, onClose, onSaved }: { rule: Rule; onClose: () => void; onSaved: () => void }) {
  const [warn, setWarn] = useState(String(rule.warnMinutes))
  const [delta, setDelta] = useState(fromNumber(rule.criticalDelta))
  const [warnDelta, setWarnDelta] = useState(fromNumber(rule.warnDelta))
  const [critical, setCritical] = useState(String(rule.criticalMinutes))
  const [clear, setClear] = useState(String(rule.clearMinutes))
  const [enabled, setEnabled] = useState(rule.enabled)

  return (
    <FormDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={rule.name}
      onSubmit={async () => {
        await api(`/rules/${rule.code}`, {
          method: "PUT",
          json: {
            warnMinutes: Number(warn),
            warnDelta: toNumber(warnDelta),
            criticalDelta: toNumber(delta),
            criticalMinutes: Number(critical),
            clearMinutes: Number(clear),
            enabled,
          },
        })
        onSaved()
      }}
    >
      {isRecordRule(rule) ? (
        <div className="grid grid-cols-2 gap-3">
          <p className="col-span-2 text-sm text-zinc-600 dark:text-zinc-300">
            {rule.code === "FLOCK_MORTALITY_DAILY"
              ? "Падёж и выбраковка за сутки сравниваются с нормой дня: во сколько раз выше нормы — предупреждение и критично."
              : "Расход за сутки сравнивается с прошлыми сутками: на сколько процентов упал — предупреждение и критично."}{" "}
            Проверяется при каждом вводе и правке учёта.
          </p>
          <Field label={`Предупреждение, ${recordRuleUnit(rule)}`}>
            <input className={inputClass} inputMode="decimal" value={warnDelta} onChange={(event) => setWarnDelta(event.target.value)} />
          </Field>
          <Field label={`Критично, ${recordRuleUnit(rule)}`}>
            <input className={inputClass} inputMode="decimal" value={delta} onChange={(event) => setDelta(event.target.value)} />
          </Field>
        </div>
      ) : (
      <div className="grid grid-cols-2 gap-3">
        <Field label="Минут вне нормы до предупреждения">
          <input className={inputClass} type="number" min={0} value={warn} onChange={(event) => setWarn(event.target.value)} />
        </Field>
        <Field label="Критично: выход за границу на">
          <input className={inputClass} inputMode="decimal" value={delta} onChange={(event) => setDelta(event.target.value)} />
        </Field>
        <Field label="Минут до критичного">
          <input className={inputClass} type="number" min={0} value={critical} onChange={(event) => setCritical(event.target.value)} />
        </Field>
        <Field label="Минут в норме до закрытия">
          <input className={inputClass} type="number" min={0} value={clear} onChange={(event) => setClear(event.target.value)} />
        </Field>
      </div>
      )}
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} /> Правило включено
      </label>
    </FormDialog>
  )
}
