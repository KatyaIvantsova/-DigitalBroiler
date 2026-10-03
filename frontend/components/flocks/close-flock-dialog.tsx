"use client"

import { useState } from "react"
import { Field, inputClass } from "@/components/app/app-shell"
import { FormDialog } from "@/components/app/form-dialog"
import { api } from "@/lib/api"
import { todayIso, type Flock } from "@/lib/types"

// Закрытие партии: дата убоя/вывоза, сдано голов и живого веса (S2-02).
export function CloseFlockDialog({
  flock,
  open,
  onOpenChange,
  onClosed,
}: {
  flock: Flock
  open: boolean
  onOpenChange: (open: boolean) => void
  onClosed: () => void
}) {
  const [closedAt, setClosedAt] = useState(todayIso())
  const [shippedHeads, setShippedHeads] = useState(String(flock.currentHeads))
  const [weight, setWeight] = useState("")

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={`Закрыть партию ${flock.code}`}
      submitLabel="Закрыть партию"
      onSubmit={async () => {
        await api(`/flocks/${flock.id}/close`, {
          method: "POST",
          json: { closedAt, shippedHeads: Number(shippedHeads), shippedLiveWeightKg: Number(weight.replace(",", ".")) },
        })
        onClosed()
      }}
    >
      <Field label="Дата убоя / вывоза">
        <input type="date" className={inputClass} min={flock.placedAt} max={todayIso()} value={closedAt} onChange={(event) => setClosedAt(event.target.value)} required />
      </Field>
      <Field label="Сдано, голов" hint={`Живое поголовье по учёту: ${flock.currentHeads}`}>
        <input type="number" min={0} max={flock.currentHeads} className={inputClass} value={shippedHeads} onChange={(event) => setShippedHeads(event.target.value)} required />
      </Field>
      <Field label="Сдано живого веса, кг">
        <input type="number" min={0} step="0.1" className={inputClass} value={weight} onChange={(event) => setWeight(event.target.value)} required />
      </Field>
      <p className="text-xs text-zinc-500">После закрытия учёт правят только технолог и администратор.</p>
    </FormDialog>
  )
}
