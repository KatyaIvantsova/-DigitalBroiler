"use client"

import { useState } from "react"
import { Plus } from "lucide-react"
import { AppShell, ErrorNote, Field, Panel, inputClass } from "@/components/app/app-shell"
import { FormDialog } from "@/components/app/form-dialog"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { useApi } from "@/hooks/use-api"
import { api } from "@/lib/api"
import { ROLE_LABELS } from "@/lib/auth"
import type { AdminUser, House, Role } from "@/lib/types"

type UserForm = {
  username: string
  fullName: string
  position: string
  role: Role
  enabled: boolean
  password: string
  houseIds: string[]
}

const ROLE_HINTS: Record<Role, string> = {
  OPERATOR: "ввод учёта и работа с инцидентами только по назначенным птичникам",
  TECHNOLOGIST: "партии, нормы, учёт по всем птичникам",
  VETERINARIAN: "учёт, инциденты по состоянию стада",
  MANAGER: "просмотр всего, KPI и отчёты, без правки справочников",
  ADMIN: "пользователи, птичники, датчики, всё остальное",
}

const toForm = (user?: AdminUser): UserForm => ({
  username: user?.username ?? "",
  fullName: user?.fullName ?? "",
  position: user?.position ?? "",
  role: user?.role ?? "OPERATOR",
  enabled: user?.enabled ?? true,
  password: "",
  houseIds: user?.houseIds ?? [],
})

// Управление пользователями (S2-05): создание, роль, блокировка, пароль, назначение на птичники.
export default function UsersPage() {
  const users = useApi<AdminUser[]>("/users")
  const houses = useApi<House[]>("/houses")
  const [editing, setEditing] = useState<AdminUser | "new" | null>(null)
  const [form, setForm] = useState<UserForm>(toForm())
  const set = (patch: Partial<UserForm>) => setForm((current) => ({ ...current, ...patch }))
  const houseName = (id: string) => houses.data?.find((house) => house.id === id)?.name ?? "?"

  const open = (user: AdminUser | "new") => {
    setForm(toForm(user === "new" ? undefined : user))
    setEditing(user)
  }

  const submit = async () => {
    const body = { ...form, password: form.password || null }
    if (editing === "new") {
      await api("/users", { method: "POST", json: body })
    } else if (editing) {
      await api(`/users/${editing.id}`, { method: "PUT", json: body })
    }
    users.reload()
  }

  const toggleHouse = (id: string) =>
    set({ houseIds: form.houseIds.includes(id) ? form.houseIds.filter((item) => item !== id) : [...form.houseIds, id] })

  return (
    <AppShell
      title="Пользователи"
      actions={
        <Button onClick={() => open("new")}>
          <Plus className="mr-1 size-4" /> Добавить пользователя
        </Button>
      }
    >
      <Panel>
        <ErrorNote message={users.error} />
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ФИО</TableHead>
              <TableHead>Логин</TableHead>
              <TableHead>Должность</TableHead>
              <TableHead>Роль</TableHead>
              <TableHead>Птичники</TableHead>
              <TableHead>Статус</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.data?.map((user) => (
              <TableRow key={user.id} className="cursor-pointer" onClick={() => open(user)}>
                <TableCell className="font-medium">{user.fullName}</TableCell>
                <TableCell>{user.username ?? "—"}</TableCell>
                <TableCell>{user.position}</TableCell>
                <TableCell>{ROLE_LABELS[user.role]}</TableCell>
                <TableCell className="text-sm text-zinc-600">
                  {user.role === "OPERATOR" ? user.houseIds.map(houseName).join(", ") || "не назначен" : "все"}
                </TableCell>
                <TableCell>{!user.enabled ? "Заблокирован" : user.canLogin ? "Активен" : "Без входа"}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Panel>

      <FormDialog
        open={editing !== null}
        onOpenChange={(next) => !next && setEditing(null)}
        title={editing === "new" ? "Новый пользователь" : "Пользователь"}
        onSubmit={submit}
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="ФИО">
            <input className={inputClass} value={form.fullName} onChange={(event) => set({ fullName: event.target.value })} required />
          </Field>
          <Field label="Должность">
            <input className={inputClass} value={form.position} onChange={(event) => set({ position: event.target.value })} required placeholder="Старший смены" />
          </Field>
          <Field label="Логин">
            <input className={inputClass} value={form.username} onChange={(event) => set({ username: event.target.value })} required pattern="[A-Za-z0-9._-]+" />
          </Field>
          <Field label={editing === "new" ? "Пароль" : "Новый пароль"} hint={editing === "new" ? "не короче 8 символов" : "пусто — оставить прежний"}>
            <input type="password" className={inputClass} value={form.password} onChange={(event) => set({ password: event.target.value })} minLength={8} required={editing === "new"} autoComplete="new-password" />
          </Field>
        </div>
        <Field label="Роль" hint={ROLE_HINTS[form.role]}>
          <select className={inputClass} value={form.role} onChange={(event) => set({ role: event.target.value as Role })}>
            {Object.entries(ROLE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </Field>
        <fieldset className="grid gap-1 text-sm">
          <legend className="mb-1 text-zinc-600 dark:text-zinc-300">Птичники {form.role !== "OPERATOR" && "(для оператора; остальные роли видят все)"}</legend>
          {houses.data?.map((house) => (
            <label key={house.id} className="flex items-center gap-2">
              <input type="checkbox" checked={form.houseIds.includes(house.id)} onChange={() => toggleHouse(house.id)} />
              {house.name}
            </label>
          ))}
        </fieldset>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={form.enabled} onChange={(event) => set({ enabled: event.target.checked })} />
          Может входить в систему
        </label>
      </FormDialog>
    </AppShell>
  )
}
