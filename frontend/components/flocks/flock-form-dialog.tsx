"use client"

import { useState } from "react"
import { FormDialog } from "@/components/app/form-dialog"
import { Field, inputClass } from "@/components/app/app-shell"
import { api } from "@/lib/api"
import { SEX_LABELS, todayIso, type Breed, type Flock, type House } from "@/lib/types"

type FlockForm = {
  houseId: string
  code: string
  breedCode: string
  placedAt: string
  placedHeads: string
  placedAvgWeightG: string
  sex: Flock["sex"]
  hatchery: string
  targetAgeDays: string
}

function initialForm(flock: Flock | undefined, houses: House[], breeds: Breed[]): FlockForm {
  if (flock) {
    return {
      houseId: flock.houseId,
      code: flock.code,
      breedCode: flock.breedCode,
      placedAt: flock.placedAt,
      placedHeads: String(flock.placedHeads),
      placedAvgWeightG: flock.placedAvgWeightG?.toString() ?? "",
      sex: flock.sex,
      hatchery: flock.hatchery ?? "",
      targetAgeDays: flock.targetAgeDays?.toString() ?? "",
    }
  }
  const freeHouse = houses.find((house) => !house.activeFlockId) ?? houses[0]
  return {
    houseId: freeHouse?.id ?? "",
    code: "",
    breedCode: breeds[0]?.code ?? "ROSS_308",
    placedAt: todayIso(),
    placedHeads: "",
    placedAvgWeightG: "42",
    sex: "MIXED",
    hatchery: "",
    targetAgeDays: "40",
  }
}

const numberOrNull = (value: string) => (value.trim() === "" ? null : Number(value.replace(",", ".")))

// Посадка новой партии или правка существующей (S2-02).
export function FlockFormDialog({
  open,
  onOpenChange,
  flock,
  houses,
  breeds,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  flock?: Flock
  houses: House[]
  breeds: Breed[]
  onSaved: (flock: Flock) => void
}) {
  const [form, setForm] = useState<FlockForm>(() => initialForm(flock, houses, breeds))
  const set = (patch: Partial<FlockForm>) => setForm((current) => ({ ...current, ...patch }))

  const submit = async () => {
    const body = {
      houseId: form.houseId,
      code: form.code.trim() || null,
      breedCode: form.breedCode,
      placedAt: form.placedAt,
      placedHeads: numberOrNull(form.placedHeads),
      placedAvgWeightG: numberOrNull(form.placedAvgWeightG),
      sex: form.sex,
      hatchery: form.hatchery.trim() || null,
      targetAgeDays: numberOrNull(form.targetAgeDays),
    }
    const saved = flock
      ? await api<Flock>(`/flocks/${flock.id}`, { method: "PUT", json: body })
      : await api<Flock>("/flocks", { method: "POST", json: body })
    onSaved(saved)
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={flock ? `Партия ${flock.code}` : "Посадка партии"}
      submitLabel={flock ? "Сохранить" : "Посадить"}
      onSubmit={submit}
    >
      <Field label="Птичник">
        <select className={inputClass} value={form.houseId} disabled={Boolean(flock)} onChange={(event) => set({ houseId: event.target.value })} required>
          {houses.map((house) => (
            <option key={house.id} value={house.id}>
              {house.name}
              {house.activeFlockId && house.activeFlockId !== flock?.id ? " — занят" : ""}
            </option>
          ))}
        </select>
      </Field>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Кросс">
          <select className={inputClass} value={form.breedCode} onChange={(event) => set({ breedCode: event.target.value })}>
            {breeds.map((breed) => (
              <option key={breed.code} value={breed.code}>
                {breed.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Дата посадки (день 0)">
          <input type="date" className={inputClass} value={form.placedAt} onChange={(event) => set({ placedAt: event.target.value })} required />
        </Field>
        <Field label="Посажено, голов">
          <input type="number" min={1} className={inputClass} value={form.placedHeads} onChange={(event) => set({ placedHeads: event.target.value })} required />
        </Field>
        <Field label="Средний вес при посадке, г">
          <input type="number" min={1} step="0.1" className={inputClass} value={form.placedAvgWeightG} onChange={(event) => set({ placedAvgWeightG: event.target.value })} />
        </Field>
        <Field label="Пол">
          <select className={inputClass} value={form.sex} onChange={(event) => set({ sex: event.target.value as Flock["sex"] })}>
            {Object.entries(SEX_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Плановый срок выращивания, дн">
          <input type="number" min={1} max={120} className={inputClass} value={form.targetAgeDays} onChange={(event) => set({ targetAgeDays: event.target.value })} />
        </Field>
      </div>
      <Field label="Инкубаторий / поставщик">
        <input className={inputClass} value={form.hatchery} onChange={(event) => set({ hatchery: event.target.value })} />
      </Field>
      <Field label="Код партии" hint="Пусто — код будет вида ГГГГ-ММ-код птичника">
        <input className={inputClass} value={form.code} onChange={(event) => set({ code: event.target.value })} maxLength={32} />
      </Field>
    </FormDialog>
  )
}
