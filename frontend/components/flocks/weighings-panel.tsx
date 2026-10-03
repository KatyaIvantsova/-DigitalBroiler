"use client"

import { useState } from "react"
import { Plus, Trash2 } from "lucide-react"
import { Field, inputClass } from "@/components/app/app-shell"
import { FormDialog } from "@/components/app/form-dialog"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { api } from "@/lib/api"
import { formatDateTime, formatNumber, type Flock, type Weighing, type Zone } from "@/lib/types"

const METHOD_LABELS: Record<Weighing["method"], string> = { MANUAL: "Ручное", SCALE: "Автовесы", VIDEO: "Видео" }

const nowLocal = () => {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

// Контрольные взвешивания партии (S2-03).
export function WeighingsPanel({
  flock,
  weighings,
  zones,
  canEdit,
  onChanged,
}: {
  flock: Flock
  weighings: Weighing[]
  zones: Zone[]
  canEdit: boolean
  onChanged: () => void
}) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ weighedAt: nowLocal(), zoneId: "", sampleHeads: "100", avgWeightG: "", uniformityPct: "", method: "MANUAL" })
  const set = (patch: Partial<typeof form>) => setForm((current) => ({ ...current, ...patch }))
  const zoneName = (id: string | null) => zones.find((zone) => zone.id === id)?.name ?? "—"

  const submit = async () => {
    await api(`/flocks/${flock.id}/weighings`, {
      method: "POST",
      json: {
        weighedAt: new Date(form.weighedAt).toISOString(),
        zoneId: form.zoneId || null,
        sampleHeads: Number(form.sampleHeads),
        avgWeightG: Number(form.avgWeightG.replace(",", ".")),
        uniformityPct: form.uniformityPct ? Number(form.uniformityPct.replace(",", ".")) : null,
        method: form.method,
      },
    })
    onChanged()
  }

  const remove = async (weighing: Weighing) => {
    if (!window.confirm(`Удалить взвешивание ${formatNumber(weighing.avgWeightG)} г?`)) return
    await api(`/flocks/${flock.id}/weighings/${weighing.id}`, { method: "DELETE" })
    onChanged()
  }

  return (
    <div className="space-y-3">
      {canEdit && (
        <Button
          size="sm"
          onClick={() => {
            set({ weighedAt: nowLocal(), avgWeightG: "", uniformityPct: "" })
            setOpen(true)
          }}
        >
          <Plus className="mr-1 size-4" /> Внести взвешивание
        </Button>
      )}
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Когда</TableHead>
            <TableHead className="text-right">День</TableHead>
            <TableHead>Зона</TableHead>
            <TableHead className="text-right">Выборка, гол</TableHead>
            <TableHead className="text-right">Средний вес, г</TableHead>
            <TableHead className="text-right">Однородность, %</TableHead>
            <TableHead>Способ</TableHead>
            {canEdit && <TableHead />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {[...weighings].reverse().map((weighing) => (
            <TableRow key={weighing.id}>
              <TableCell>{formatDateTime(weighing.weighedAt)}</TableCell>
              <TableCell className="text-right">{weighing.ageDay}</TableCell>
              <TableCell>{zoneName(weighing.zoneId)}</TableCell>
              <TableCell className="text-right">{weighing.sampleHeads}</TableCell>
              <TableCell className="text-right">{formatNumber(weighing.avgWeightG, 1)}</TableCell>
              <TableCell className="text-right">{formatNumber(weighing.uniformityPct, 1)}</TableCell>
              <TableCell>{METHOD_LABELS[weighing.method]}</TableCell>
              {canEdit && (
                <TableCell className="text-right">
                  <Button size="icon" variant="ghost" aria-label="Удалить" onClick={() => void remove(weighing)}>
                    <Trash2 className="size-4" />
                  </Button>
                </TableCell>
              )}
            </TableRow>
          ))}
          {weighings.length === 0 && (
            <TableRow>
              <TableCell colSpan={8} className="py-8 text-center text-zinc-500">
                Взвешиваний пока нет
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>

      <FormDialog open={open} onOpenChange={setOpen} title="Контрольное взвешивание" onSubmit={submit}>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Когда">
            <input type="datetime-local" className={inputClass} value={form.weighedAt} onChange={(event) => set({ weighedAt: event.target.value })} required />
          </Field>
          <Field label="Зона">
            <select className={inputClass} value={form.zoneId} onChange={(event) => set({ zoneId: event.target.value })}>
              <option value="">По птичнику</option>
              {zones.map((zone) => (
                <option key={zone.id} value={zone.id}>
                  {zone.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Взвешено птиц">
            <input type="number" min={1} className={inputClass} value={form.sampleHeads} onChange={(event) => set({ sampleHeads: event.target.value })} required />
          </Field>
          <Field label="Средний вес, г">
            <input type="number" min={1} step="0.1" className={inputClass} value={form.avgWeightG} onChange={(event) => set({ avgWeightG: event.target.value })} required />
          </Field>
          <Field label="Однородность, %" hint="Доля птиц в пределах ±10% от среднего">
            <input type="number" min={0} max={100} step="0.1" className={inputClass} value={form.uniformityPct} onChange={(event) => set({ uniformityPct: event.target.value })} />
          </Field>
          <Field label="Способ">
            <select className={inputClass} value={form.method} onChange={(event) => set({ method: event.target.value })}>
              {Object.entries(METHOD_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </Field>
        </div>
      </FormDialog>
    </div>
  )
}
