"use client"

import { useState } from "react"
import { Field } from "@/components/app/app-shell"
import { FormDialog } from "@/components/app/form-dialog"
import { api } from "@/lib/api"
import type { Flock, FlockImportResult } from "@/lib/types"

const TEMPLATE = "date;mortality;culled;feed_kg;water_l;weight_g;sample_heads;comment"

/** Импорт учёта из CSV учётной системы (S4-07, docs/sprint2/09-integration-protocol.md п. 3). */
export function ImportRecordsDialog({ flock, onClose, onImported }: { flock: Flock; onClose: () => void; onImported: () => void }) {
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<FlockImportResult>()

  return (
    <FormDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={`Импорт учёта: партия ${flock.code}`}
      submitLabel="Загрузить"
      onSubmit={async () => {
        if (!file) throw new Error("Выберите файл")
        const form = new FormData()
        form.append("file", file)
        const imported = await api<FlockImportResult>(`/flocks/${flock.id}/import`, { method: "POST", body: form })
        setResult(imported)
        if (!imported.applied) {
          throw new Error(`В файле есть ошибки, ничего не сохранено. ${imported.errors.slice(0, 10).join("; ")}${imported.errors.length > 10 ? ` и ещё ${imported.errors.length - 10}` : ""}`)
        }
        onImported()
      }}
    >
      <p className="text-sm text-zinc-600 dark:text-zinc-300">
        CSV с разделителем «;» в UTF-8 или Windows-1251 (выгрузка из 1С). Первая строка — заголовок:
      </p>
      <code className="block overflow-x-auto rounded-lg bg-zinc-100 px-2 py-1 text-xs dark:bg-zinc-900">{TEMPLATE}</code>
      <p className="text-xs text-zinc-500">
        Дата — ДД.ММ.ГГГГ или ГГГГ-ММ-ДД, дробные числа — через запятую или точку. Строка за уже внесённую дату исправляет запись, вес и
        выборка создают взвешивание. Если в файле есть ошибка, не сохраняется ничего.
      </p>
      <Field label="Файл CSV">
        <input type="file" accept=".csv,text/csv" className="text-sm" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
      </Field>
      {result?.applied && (
        <p className="text-sm">
          Строк: {result.rows}. Новых: {result.created}, исправлено: {result.updated}, без изменений: {result.unchanged}, взвешиваний: {result.weighings}
        </p>
      )}
    </FormDialog>
  )
}
