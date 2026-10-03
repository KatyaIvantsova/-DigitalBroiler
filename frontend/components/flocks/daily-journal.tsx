"use client"

import { useState } from "react"
import { Pencil, Plus } from "lucide-react"
import { Field, inputClass } from "@/components/app/app-shell"
import { FormDialog } from "@/components/app/form-dialog"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { api } from "@/lib/api"
import { formatDate, formatNumber, todayIso, type DailyRecord, type Flock } from "@/lib/types"

type RecordForm = {
  recordDate: string
  mortalityHeads: string
  culledHeads: string
  feedConsumedKg: string
  waterConsumedL: string
  comment: string
}

const emptyForm = (): RecordForm => ({
  recordDate: todayIso(),
  mortalityHeads: "0",
  culledHeads: "0",
  feedConsumedKg: "",
  waterConsumedL: "",
  comment: "",
})

const toForm = (record: DailyRecord): RecordForm => ({
  recordDate: record.recordDate,
  mortalityHeads: String(record.mortalityHeads),
  culledHeads: String(record.culledHeads),
  feedConsumedKg: record.feedConsumedKg?.toString() ?? "",
  waterConsumedL: record.waterConsumedL?.toString() ?? "",
  comment: record.comment ?? "",
})

const numberOrNull = (value: string) => (value.trim() === "" ? null : Number(value.replace(",", ".")))

// Ежедневный учёт по партии (S2-03): ввод и правка за любой день цикла, правки уходят в журнал действий.
export function DailyJournal({
  flock,
  records,
  canEdit,
  onChanged,
}: {
  flock: Flock
  records: DailyRecord[]
  canEdit: boolean
  onChanged: () => void
}) {
  const [editing, setEditing] = useState<DailyRecord | "new" | null>(null)
  const [form, setForm] = useState<RecordForm>(emptyForm)
  const set = (patch: Partial<RecordForm>) => setForm((current) => ({ ...current, ...patch }))

  const open = (record: DailyRecord | "new") => {
    if (record === "new") {
      const filled = new Set(records.map((item) => item.recordDate))
      const initial = emptyForm()
      if (filled.has(initial.recordDate)) initial.recordDate = ""
      setForm(initial)
    } else {
      setForm(toForm(record))
    }
    setEditing(record)
  }

  const submit = async () => {
    const body = {
      recordDate: form.recordDate,
      mortalityHeads: numberOrNull(form.mortalityHeads) ?? 0,
      culledHeads: numberOrNull(form.culledHeads) ?? 0,
      feedConsumedKg: numberOrNull(form.feedConsumedKg),
      waterConsumedL: numberOrNull(form.waterConsumedL),
      comment: form.comment.trim() || null,
    }
    if (editing && editing !== "new") {
      await api(`/flocks/${flock.id}/daily-records/${editing.id}`, { method: "PUT", json: body })
    } else {
      await api(`/flocks/${flock.id}/daily-records`, { method: "POST", json: body })
    }
    onChanged()
  }

  const rows = [...records].reverse()

  return (
    <div className="space-y-3">
      {canEdit && (
        <Button size="sm" onClick={() => open("new")}>
          <Plus className="mr-1 size-4" /> Внести учёт за день
        </Button>
      )}
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Дата</TableHead>
            <TableHead className="text-right">День</TableHead>
            <TableHead className="text-right">Падёж</TableHead>
            <TableHead className="text-right">Выбраковка</TableHead>
            <TableHead className="text-right">Корм, кг</TableHead>
            <TableHead className="text-right">Вода, л</TableHead>
            <TableHead className="text-right">Поголовье</TableHead>
            <TableHead>Комментарий</TableHead>
            {canEdit && <TableHead />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((record) => (
            <TableRow key={record.id}>
              <TableCell>{formatDate(record.recordDate)}</TableCell>
              <TableCell className="text-right">{record.ageDay}</TableCell>
              <TableCell className="text-right">{record.mortalityHeads}</TableCell>
              <TableCell className="text-right">{record.culledHeads}</TableCell>
              <TableCell className="text-right">{formatNumber(record.feedConsumedKg, 1)}</TableCell>
              <TableCell className="text-right">{formatNumber(record.waterConsumedL, 1)}</TableCell>
              <TableCell className="text-right">{formatNumber(record.headsAtEnd)}</TableCell>
              <TableCell className="max-w-[240px] truncate text-zinc-500">{record.comment}</TableCell>
              {canEdit && (
                <TableCell className="text-right">
                  <Button size="icon" variant="ghost" aria-label="Исправить" onClick={() => open(record)}>
                    <Pencil className="size-4" />
                  </Button>
                </TableCell>
              )}
            </TableRow>
          ))}
          {rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={9} className="py-8 text-center text-zinc-500">
                Учёт по партии ещё не вносился
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>

      <FormDialog
        open={editing !== null}
        onOpenChange={(next) => !next && setEditing(null)}
        title={editing && editing !== "new" ? `Учёт за ${formatDate(editing.recordDate)}` : "Учёт за день"}
        onSubmit={submit}
      >
        <Field label="Дата">
          <input type="date" className={inputClass} min={flock.placedAt} max={flock.closedAt ?? todayIso()} value={form.recordDate} onChange={(event) => set({ recordDate: event.target.value })} required />
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Падёж, гол">
            <input type="number" min={0} className={inputClass} value={form.mortalityHeads} onChange={(event) => set({ mortalityHeads: event.target.value })} required />
          </Field>
          <Field label="Выбраковка, гол">
            <input type="number" min={0} className={inputClass} value={form.culledHeads} onChange={(event) => set({ culledHeads: event.target.value })} required />
          </Field>
          <Field label="Расход корма, кг">
            <input type="number" min={0} step="0.1" className={inputClass} value={form.feedConsumedKg} onChange={(event) => set({ feedConsumedKg: event.target.value })} />
          </Field>
          <Field label="Расход воды, л">
            <input type="number" min={0} step="0.1" className={inputClass} value={form.waterConsumedL} onChange={(event) => set({ waterConsumedL: event.target.value })} />
          </Field>
        </div>
        <Field label="Комментарий">
          <textarea className={`${inputClass} h-20 py-2`} value={form.comment} onChange={(event) => set({ comment: event.target.value })} />
        </Field>
      </FormDialog>
    </div>
  )
}
