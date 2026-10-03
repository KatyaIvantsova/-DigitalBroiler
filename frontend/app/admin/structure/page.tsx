"use client"

import { useState } from "react"
import { Plus } from "lucide-react"
import { AppShell, ErrorNote, Field, Panel, inputClass } from "@/components/app/app-shell"
import { FormDialog } from "@/components/app/form-dialog"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { useApi } from "@/hooks/use-api"
import { api } from "@/lib/api"
import type { House, Sensor, Site } from "@/lib/types"

// Птичники, зоны и справочник датчиков (S2-01, S2-07): датчик переносится в другую зону без правки кода.
export default function StructurePage() {
  const sites = useApi<Site[]>("/sites")
  const houses = useApi<House[]>("/houses")
  const sensors = useApi<Sensor[]>("/sensors")
  const [error, setError] = useState<string>()
  const [houseDialog, setHouseDialog] = useState(false)
  const [houseForm, setHouseForm] = useState({ siteId: "", code: "", name: "", areaM2: "", capacityHeads: "" })
  const [zoneHouse, setZoneHouse] = useState<House | null>(null)
  const [zoneForm, setZoneForm] = useState({ code: "", name: "" })

  const moveSensor = async (sensor: Sensor, houseId: string | null, zoneId: string | null) => {
    setError(undefined)
    try {
      await api(`/sensors/${sensor.id}/location`, { method: "PATCH", json: { houseId, zoneId } })
      sensors.reload()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason))
    }
  }

  const sortedSensors = [...(sensors.data ?? [])].sort((a, b) => a.code.localeCompare(b.code))

  return (
    <AppShell
      title="Птичники и датчики"
      actions={
        <Button
          onClick={() => {
            setHouseForm({ siteId: sites.data?.[0]?.id ?? "", code: "", name: "", areaM2: "", capacityHeads: "" })
            setHouseDialog(true)
          }}
        >
          <Plus className="mr-1 size-4" /> Добавить птичник
        </Button>
      }
    >
      <ErrorNote message={houses.error ?? sensors.error ?? error} />
      <div className="grid gap-4 xl:grid-cols-2">
        {houses.data?.map((house) => (
          <Panel
            key={house.id}
            title={`${house.name} (${house.code})`}
            actions={
              <Button
                size="sm"
                variant="outline"
                onClick={() => {
                  setZoneForm({ code: `Z${house.zones.length + 1}`, name: `Зона ${house.zones.length + 1}` })
                  setZoneHouse(house)
                }}
              >
                <Plus className="mr-1 size-4" /> Зона
              </Button>
            }
          >
            <p className="mb-2 text-sm text-zinc-500">
              {house.siteName} · {house.areaM2 ? `${house.areaM2} м²` : "площадь не задана"} ·{" "}
              {house.capacityHeads ? `до ${house.capacityHeads} гол` : "вместимость не задана"}
            </p>
            <div className="flex flex-wrap gap-2">
              {house.zones.map((zone) => (
                <span key={zone.id} className="dashboard-chip">
                  {zone.code} · {zone.name} · датчиков {sortedSensors.filter((sensor) => sensor.zoneId === zone.id).length}
                </span>
              ))}
              {house.zones.length === 0 && <span className="text-sm text-zinc-500">Зон нет</span>}
            </div>
          </Panel>
        ))}
      </div>

      <Panel title="Датчики">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Код</TableHead>
              <TableHead>Название</TableHead>
              <TableHead>Тип</TableHead>
              <TableHead>Птичник</TableHead>
              <TableHead>Зона</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {sortedSensors.map((sensor) => {
              const house = houses.data?.find((item) => item.id === sensor.houseId)
              return (
                <TableRow key={sensor.id}>
                  <TableCell className="font-mono text-xs">{sensor.code}</TableCell>
                  <TableCell>{sensor.name}</TableCell>
                  <TableCell>{sensor.type}</TableCell>
                  <TableCell>
                    <select className={inputClass} value={sensor.houseId ?? ""} onChange={(event) => void moveSensor(sensor, event.target.value || null, null)}>
                      <option value="">— не установлен —</option>
                      {houses.data?.map((item) => (
                        <option key={item.id} value={item.id}>
                          {item.name}
                        </option>
                      ))}
                    </select>
                  </TableCell>
                  <TableCell>
                    <select className={inputClass} value={sensor.zoneId ?? ""} disabled={!house} onChange={(event) => void moveSensor(sensor, sensor.houseId, event.target.value || null)}>
                      <option value="">— весь птичник —</option>
                      {house?.zones.map((zone) => (
                        <option key={zone.id} value={zone.id}>
                          {zone.name}
                        </option>
                      ))}
                    </select>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </Panel>

      <FormDialog
        open={houseDialog}
        onOpenChange={setHouseDialog}
        title="Новый птичник"
        onSubmit={async () => {
          await api("/houses", {
            method: "POST",
            json: {
              siteId: houseForm.siteId,
              code: houseForm.code,
              name: houseForm.name,
              areaM2: houseForm.areaM2 ? Number(houseForm.areaM2) : null,
              capacityHeads: houseForm.capacityHeads ? Number(houseForm.capacityHeads) : null,
            },
          })
          houses.reload()
        }}
      >
        <Field label="Площадка">
          <select className={inputClass} value={houseForm.siteId} onChange={(event) => setHouseForm({ ...houseForm, siteId: event.target.value })} required>
            {sites.data?.map((site) => (
              <option key={site.id} value={site.id}>
                {site.name}
              </option>
            ))}
          </select>
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Код">
            <input className={inputClass} value={houseForm.code} onChange={(event) => setHouseForm({ ...houseForm, code: event.target.value })} required placeholder="1-05" />
          </Field>
          <Field label="Название">
            <input className={inputClass} value={houseForm.name} onChange={(event) => setHouseForm({ ...houseForm, name: event.target.value })} required placeholder="Птичник 5" />
          </Field>
          <Field label="Площадь, м²">
            <input type="number" min={1} className={inputClass} value={houseForm.areaM2} onChange={(event) => setHouseForm({ ...houseForm, areaM2: event.target.value })} />
          </Field>
          <Field label="Вместимость, гол">
            <input type="number" min={1} className={inputClass} value={houseForm.capacityHeads} onChange={(event) => setHouseForm({ ...houseForm, capacityHeads: event.target.value })} />
          </Field>
        </div>
      </FormDialog>

      <FormDialog
        open={zoneHouse !== null}
        onOpenChange={(next) => !next && setZoneHouse(null)}
        title={`Новая зона: ${zoneHouse?.name ?? ""}`}
        onSubmit={async () => {
          if (!zoneHouse) return
          await api(`/houses/${zoneHouse.id}/zones`, { method: "POST", json: zoneForm })
          houses.reload()
        }}
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Код">
            <input className={inputClass} value={zoneForm.code} onChange={(event) => setZoneForm({ ...zoneForm, code: event.target.value })} required />
          </Field>
          <Field label="Название">
            <input className={inputClass} value={zoneForm.name} onChange={(event) => setZoneForm({ ...zoneForm, name: event.target.value })} required />
          </Field>
        </div>
      </FormDialog>
    </AppShell>
  )
}
