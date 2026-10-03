"use client"

import { useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import {
  AlertTriangle,
  CheckCircle2,
  ClipboardPlus,
  Clock3,
  Droplets,
  ExternalLink,
  Download,
  Link2,
  Loader2,
  PlayCircle,
  RefreshCcw,
  Search,
  ShieldAlert,
  Thermometer,
  TimerReset,
  ChevronDown,
  ChevronRight,
  UserRound,
  Wind,
  Wrench,
  type LucideIcon,
} from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { IncidentAttachmentPicker } from "@/components/dashboard/incident-attachment-picker"
import { Input } from "@/components/ui/input"
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Textarea } from "@/components/ui/textarea"
import { uploadIncidentAttachments } from "@/lib/incident-attachments"
import { cn } from "@/lib/utils"
import { useCurrentUser } from "@/hooks/use-current-user"

type IncidentPriority = "critical" | "high" | "medium" | "low"
type IncidentStatus = "new" | "inProgress" | "overdue" | "resolved" | "closed" | "cancelled"

interface BackendIncident {
  id: string
  code?: string | null
  title: string
  description?: string | null
  type?: string | null
  workshop?: string | null
  house?: string | null
  zone?: string | null
  status: string
  priority: string
  source: string
  responsible?: string | null
  assigneeId?: string | null
  assigneeRole?: string | null
  decisionComment?: string | null
  createdAt?: string | null
  detectedAt?: string | null
  startedAt?: string | null
  reactionMinutes?: number | null
}

interface Incident {
  backendId?: string
  id: string
  date: string
  time: string
  type: string
  icon: LucideIcon
  shortDescription: string
  description: string
  workshop: string
  poultryHouse: string
  zone: string
  priority: IncidentPriority
  status: IncidentStatus
  responsible: string
  assigneeRole?: string
  startedAt?: string
  comment: string
  closedAt?: string
}

const priorityConfig: Record<IncidentPriority, { label: string; className: string; rowClass: string; iconClass: string }> = {
  critical: {
    label: "Критический",
    className: "border-red-200 bg-red-50 text-red-700",
    rowClass: "border-l-red-500",
    iconClass: "text-red-600",
  },
  high: {
    label: "Высокий",
    className: "border-amber-200 bg-amber-50 text-amber-700",
    rowClass: "border-l-amber-500",
    iconClass: "text-amber-600",
  },
  medium: {
    label: "Средний",
    className: "border-sky-200 bg-sky-50 text-sky-700",
    rowClass: "border-l-sky-500",
    iconClass: "text-sky-600",
  },
  low: {
    label: "Низкий",
    className: "border-zinc-300 bg-zinc-100 text-zinc-700",
    rowClass: "border-l-zinc-400",
    iconClass: "text-zinc-600",
  },
}

const statusConfig: Record<IncidentStatus, { label: string; className: string }> = {
  new: { label: "Новый", className: "border-emerald-200 bg-emerald-50 text-emerald-700" },
  inProgress: { label: "В работе", className: "border-sky-200 bg-sky-50 text-sky-700" },
  overdue: { label: "Просрочен", className: "border-red-200 bg-red-50 text-red-700" },
  resolved: { label: "Решён", className: "border-teal-200 bg-teal-50 text-teal-700" },
  closed: { label: "Закрыт", className: "border-zinc-300 bg-zinc-100 text-zinc-700" },
  cancelled: { label: "Отменён", className: "border-zinc-300 bg-zinc-50 text-zinc-500" },
}

const backendPriorityMap: Record<string, IncidentPriority> = {
  CRITICAL: "critical",
  HIGH: "high",
  MEDIUM: "medium",
  LOW: "low",
}

const backendStatusMap: Record<string, IncidentStatus> = {
  OPEN: "new",
  IN_PROGRESS: "inProgress",
  RESOLVED: "resolved",
  CLOSED: "closed",
  CANCELLED: "cancelled",
}

const backendIncidentTypeLabelMap: Record<string, string> = {
  MICROCLIMATE: "Микроклимат",
  SANITATION: "Санитария",
  FLOCK_HEALTH: "Падеж и состояние стада",
  FEEDING: "Кормление",
  WATER_SUPPLY: "Водоснабжение",
  PRODUCTION_METRICS: "Производственные показатели",

  LIGHTING_ILLUMINANCE_LOW: "Освещение",
  LIGHTING_ILLUMINANCE_HIGH: "Освещение",
  LIGHTING_UNIFORMITY_VIOLATION: "Освещение",
  LIGHTING_SYSTEM_HEALTH_WARNING: "Освещение",
  LIGHTING_SYSTEM_HEALTH_CRITICAL: "Освещение",
  LIGHTING_SCHEDULE_DEVIATION: "Освещение",
  LIGHTING_DARK_PERIOD_VIOLATION: "Освещение",
  LIGHTING_CONTROLLER_FAILURE: "Освещение",
  LIGHTING_CONTINUOUS_LIGHT: "Освещение",
  LIGHTING_CONTINUOUS_DARK: "Освещение",
  LIGHTING_MISSING_EVENTS: "Освещение",

  OTHER: "Прочее",
}

const backendIncidentTypeIconMap: Record<string, LucideIcon> = {
  MICROCLIMATE: Thermometer,
  SANITATION: ShieldAlert,
  FLOCK_HEALTH: ShieldAlert,
  FEEDING: Wrench,
  WATER_SUPPLY: Droplets,
  PRODUCTION_METRICS: Wrench,

  LIGHTING_ILLUMINANCE_LOW: AlertTriangle,
  LIGHTING_ILLUMINANCE_HIGH: AlertTriangle,
  LIGHTING_UNIFORMITY_VIOLATION: AlertTriangle,
  LIGHTING_SYSTEM_HEALTH_WARNING: AlertTriangle,
  LIGHTING_SYSTEM_HEALTH_CRITICAL: AlertTriangle,
  LIGHTING_SCHEDULE_DEVIATION: AlertTriangle,
  LIGHTING_DARK_PERIOD_VIOLATION: AlertTriangle,
  LIGHTING_CONTROLLER_FAILURE: AlertTriangle,
  LIGHTING_CONTINUOUS_LIGHT: AlertTriangle,
  LIGHTING_CONTINUOUS_DARK: AlertTriangle,
  LIGHTING_MISSING_EVENTS: AlertTriangle,

  OTHER: AlertTriangle,
}

const backendSourceLabelMap: Record<string, string> = {
  NOTIFICATION: "Уведомление",
  MANUAL: "Ручной",
  SYSTEM: "Система",
  ANALYTICS: "Аналитика",
}

const backendSourceIconMap: Record<string, LucideIcon> = {
  NOTIFICATION: AlertTriangle,
  MANUAL: UserRound,
  SYSTEM: Wrench,
  ANALYTICS: ShieldAlert,
}

const unknownValue = "—"
const incidentRefreshIntervalMs = 10_000

const enterpriseRoles = [
  "Директор по качеству",
  "Ветврач",
  "Ветеринарная служба",
  "Главный инженер",
  "Техническая служба",
  "Зоотехник",
  "Старший смены",
  "Оператор",
]

const normalizeBackendEnum = (value?: string | null) => value?.toUpperCase() ?? ""

const formatBackendDateTime = (value?: string | null) => {
  if (!value) {
    return { date: unknownValue, time: unknownValue }
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return { date: unknownValue, time: unknownValue }
  }

  return {
    date: date.toLocaleDateString("ru-RU"),
    time: date.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" }),
  }
}

const parseIncidentDateTime = (dateLabel: string, timeLabel: string) => {
  const [day, month, year] = dateLabel.split(".").map(Number)
  const [hour = 0, minute = 0] = timeLabel.split(":").map(Number)

  if (!day || !month || !year) {
    return undefined
  }

  const date = new Date(year, month - 1, day, hour, minute)

  return Number.isNaN(date.getTime()) ? undefined : date.getTime()
}

const getIncidentTimestamp = (incident: Incident) =>
  parseIncidentDateTime(incident.date, incident.time)

const isTerminalIncident = (incident: Incident) =>
  incident.status === "resolved" || incident.status === "closed" || incident.status === "cancelled"

const sortIncidents = (incidents: Incident[]) =>
  [...incidents].sort(
    (a, b) => (getIncidentTimestamp(b) ?? 0) - (getIncidentTimestamp(a) ?? 0),
  )

const resolveLocation = (code?: string | null) => {
  if (code === "INC-1") return { workshop: "Цех 2", poultryHouse: "Птичник 4", zone: "Зона посадки" }
  if (code === "INC-2") return { workshop: "Цех 1", poultryHouse: "Птичник 2", zone: "Основной зал" }
  if (code === "INC-3") return { workshop: "Цех 3", poultryHouse: "Птичник 1", zone: "Линия поения 2" }
  if (code === "INC-4") return { workshop: "Цех 2", poultryHouse: "Птичник 5", zone: "Модуль вентиляции" }
  if (code === "INC-5") return { workshop: "Цех 2", poultryHouse: "Птичник 5", zone: "Зона подстилки" }

  return { workshop: unknownValue, poultryHouse: unknownValue, zone: unknownValue }
}

const toIncident = (incident: BackendIncident): Incident => {
  const source = normalizeBackendEnum(incident.source)
  const type = normalizeBackendEnum(incident.type)
  const hasSpecificType = Boolean(type && type !== "OTHER")
  const { date, time } = formatBackendDateTime(incident.detectedAt ?? incident.createdAt)
  const id = incident.code ?? incident.id
  const fallbackLocation = resolveLocation(id)

  return {
    backendId: incident.id,
    id,
    date,
    time,
    type: hasSpecificType
      ? backendIncidentTypeLabelMap[type] ?? "Инцидент"
      : source === "MANUAL"
        ? backendIncidentTypeLabelMap[type] ?? "Прочее"
        : backendSourceLabelMap[source] ?? backendIncidentTypeLabelMap[type] ?? "Инцидент",
    icon: hasSpecificType
      ? backendIncidentTypeIconMap[type] ?? AlertTriangle
      : source === "MANUAL"
        ? backendIncidentTypeIconMap[type] ?? AlertTriangle
        : backendSourceIconMap[source] ?? backendIncidentTypeIconMap[type] ?? AlertTriangle,
    shortDescription: incident.title,
    description: incident.description ?? "Описание не указано.",
    workshop: incident.workshop ?? fallbackLocation.workshop,
    poultryHouse: incident.house ?? fallbackLocation.poultryHouse,
    zone: incident.zone ?? fallbackLocation.zone,
    priority: backendPriorityMap[normalizeBackendEnum(incident.priority)] ?? "medium",
    status: backendStatusMap[normalizeBackendEnum(incident.status)] ?? "new",
    responsible: incident.responsible ?? "Не назначен",
    assigneeRole: incident.assigneeRole ?? undefined,
    startedAt: incident.startedAt ?? undefined,
    comment: incident.decisionComment ?? "Комментарий не указан.",
  }
}

const buildKpiItems = (incidents: Incident[]) => [
  { label: "Открытые", value: incidents.filter((i) => !isTerminalIncident(i)).length.toString(), icon: Clock3, tone: "text-sky-600" },
  { label: "Критические", value: incidents.filter((i) => i.priority === "critical").length.toString(), icon: AlertTriangle, tone: "text-red-600" },
  { label: "Просроченные", value: incidents.filter((i) => i.status === "overdue").length.toString(), icon: TimerReset, tone: "text-amber-600" },
  { label: "Завершённые", value: incidents.filter(isTerminalIncident).length.toString(), icon: CheckCircle2, tone: "text-emerald-600" },
]

const fallbackIncidents: Incident[] = [
  {
    id: "INC-1",
    date: "15.04.2026",
    time: "10:38",
    type: "Микроклимат",
    icon: Thermometer,
    shortDescription: "Критический перегрев зоны посадки",
    description: "Температура в зоне посадки держалась выше нормы 18 минут.",
    workshop: "Цех 2",
    poultryHouse: "Птичник 4",
    zone: "Зона посадки",
    priority: "critical",
    status: "inProgress",
    responsible: "Иванов",
    comment: "Смена открыла заслонки.",
  },
  {
    id: "INC-2",
    date: "15.04.2026",
    time: "10:21",
    type: "Падеж",
    icon: ShieldAlert,
    shortDescription: "Рост падежа",
    description: "Падеж превысил плановый уровень за последние 6 часов.",
    workshop: "Цех 1",
    poultryHouse: "Птичник 2",
    zone: "Основной зал",
    priority: "high",
    status: "new",
    responsible: "Ветеринарная служба",
    comment: "Инцидент создан по уведомлению системы.",
  },
  {
    id: "INC-3",
    date: "15.04.2026",
    time: "09:56",
    type: "Поение",
    icon: Droplets,
    shortDescription: "Снижение потребления воды",
    description: "Потребление воды ниже ожидаемого уровня для партии.",
    workshop: "Цех 3",
    poultryHouse: "Птичник 1",
    zone: "Линия поения 2",
    priority: "medium",
    status: "new",
    responsible: "Соколов",
    comment: "Проверить давление и доступность ниппелей.",
  },
  {
    id: "INC-4",
    date: "15.04.2026",
    time: "09:10",
    type: "Оборудование",
    icon: Wrench,
    shortDescription: "Сбой вентилятора",
    description: "Контроллер сообщил о нестабильной работе вентилятора.",
    workshop: "Цех 2",
    poultryHouse: "Птичник 5",
    zone: "Модуль вентиляции",
    priority: "high",
    status: "closed",
    responsible: "Морозов",
    comment: "Вентилятор перезапущен, контрольный период пройден.",
    closedAt: "15.04.2026, 09:40",
  },
  {
    id: "INC-5",
    date: "14.04.2026",
    time: "18:12",
    type: "Воздух",
    icon: Wind,
    shortDescription: "Рост аммиака",
    description: "Аммиак приближается к верхней границе нормы.",
    workshop: "Цех 2",
    poultryHouse: "Птичник 5",
    zone: "Зона подстилки",
    priority: "medium",
    status: "overdue",
    responsible: "Не назначен",
    comment: "Срок первичной реакции истек.",
  },
]

function FieldSelect({ label, options, value, onValueChange }: { label: string; options: string[]; value: string; onValueChange: (value: string) => void }) {
  return (
    <label className="flex min-w-0 flex-col gap-1.5">
      <span className="text-xs uppercase tracking-wide text-zinc-500">{label}</span>
      <select value={value} onChange={(event) => onValueChange(event.target.value)} className="h-9 rounded-md border border-zinc-300 bg-white px-3 text-sm text-zinc-900 outline-none transition-colors hover:bg-zinc-50 focus:border-zinc-500">
        {options.map((option) => (
          <option key={option}>{option}</option>
        ))}
      </select>
    </label>
  )
}

const incidentMetricMap: Record<string, { metricId: string; label: string }> = {
  "INC-1": { metricId: "temperature_21_30", label: "Температура" },
  "INC-2": { metricId: "mortality_21_30", label: "Падеж" },
  "INC-3": { metricId: "water_intake_21_30", label: "Потребление воды" },
  "INC-4": { metricId: "temperature_21_30", label: "Температура / вентиляция" },
  "INC-5": { metricId: "ammonia_21_30", label: "Аммиак" },
}

const getMetricForIncident = (incident: Incident) => {
  if (incidentMetricMap[incident.id]) {
    return incidentMetricMap[incident.id]
  }

  if (incident.type.includes("оборуд") || incident.type.includes("Поломка")) {
    return { metricId: "temperature_21_30", label: "Температура / вентиляция" }
  }
  if (incident.type.includes("биобез") || incident.type.includes("Биобез")) {
    return { metricId: "ammonia_21_30", label: "Аммиак" }
  }
  if (incident.type.includes("корм") || incident.type.includes("Качество")) {
    return { metricId: "feed_intake_21_30", label: "Потребление корма" }
  }

  return { metricId: "mortality_21_30", label: "Состояние стада" }
}

function IncidentDetails({
  incident,
  assigningIncidentId,
  selectedAssigneeRole,
  onOpenDetails,
  onAssignIncident,
  onAssigneeRoleChange,
  onCloseIncident,
  onReopenIncident,
  onDownloadReport,
  onCreateRelated,
}: {
  incident: Incident
  assigningIncidentId: string | null
  selectedAssigneeRole: string
  onOpenDetails: (id: string) => void
  onAssignIncident: (incident: Incident) => void
  onAssigneeRoleChange: (role: string) => void
  onCloseIncident: (id: string) => void
  onReopenIncident: (id: string) => void
  onDownloadReport: (id: string) => void
  onCreateRelated: (id: string) => void
}) {
  const relatedMetric = getMetricForIncident(incident)
  const isAssigning = assigningIncidentId === incident.id

  return (
    <aside className="flex h-full min-h-0 flex-col rounded-br-[28px] border-l border-zinc-200 bg-white">
      <div className="border-b border-zinc-200 px-5 py-4">
        <p className="text-xs uppercase tracking-wide text-zinc-500">Карточка инцидента</p>
        <div className="mt-2 flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-foreground">{incident.id}</h2>
            <p className="mt-1 text-sm text-zinc-500">{incident.date}, {incident.time}</p>
          </div>
          <Badge className={priorityConfig[incident.priority].className}>{priorityConfig[incident.priority].label}</Badge>
        </div>
      </div>

      <div className="flex-1 space-y-5 overflow-y-auto p-5">
        <section>
          <h3 className="mb-2 text-sm font-medium text-foreground">Описание</h3>
          <p className="text-sm leading-6 text-zinc-700">{incident.description}</p>
        </section>
        <section className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-3">
            <p className="text-xs uppercase tracking-wide text-zinc-500">Цех</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">{incident.workshop}</p>
          </div>
          <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-3">
            <p className="text-xs uppercase tracking-wide text-zinc-500">Птичник</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">{incident.poultryHouse}</p>
          </div>
        </section>
        {isTerminalIncident(incident) && (
          <section className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">
            <p className="font-medium">Статус: {statusConfig[incident.status].label}{incident.closedAt ? ` (${incident.closedAt})` : ""}</p>
          </section>
        )}
        {incident.status === "inProgress" && (
          <section className="rounded-lg border border-sky-200 bg-sky-50 p-3 text-sm text-sky-900">
            <p className="text-xs uppercase tracking-wide text-sky-700">Назначение</p>
            <p className="mt-1 font-medium">В работе у: {incident.responsible}</p>
            {incident.assigneeRole && <p className="mt-1 text-sky-800">Роль: {incident.assigneeRole}</p>}
          </section>
        )}
        {incident.status === "new" && (
          <section className="rounded-lg border border-zinc-200 bg-zinc-50 p-3">
            <label className="text-xs uppercase tracking-wide text-zinc-500" htmlFor={`assign-role-${incident.id}`}>
              Роль для назначения
            </label>
            <select
              id={`assign-role-${incident.id}`}
              value={selectedAssigneeRole}
              onChange={(event) => onAssigneeRoleChange(event.target.value)}
              className="mt-2 w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none transition focus:border-zinc-500"
            >
              {enterpriseRoles.map((role) => (
                <option key={role} value={role}>
                  {role}
                </option>
              ))}
            </select>
            <p className="mt-2 text-xs text-zinc-500">Показываем только роли предприятия, без фамилий.</p>
          </section>
        )}
        {relatedMetric && (
          <section className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
            <p className="text-xs uppercase tracking-wide text-amber-700">Связанный график</p>
            <p className="mt-1 font-medium">{relatedMetric.label}</p>
          </section>
        )}
        <section>
          <p className="mb-1 text-xs uppercase tracking-wide text-zinc-500">
            {isTerminalIncident(incident) ? "Итог / решение" : "Комментарий"}
          </p>
          <p className="rounded-md border border-zinc-200 bg-zinc-50 px-3 py-2 text-sm text-zinc-700">
            {incident.comment}
          </p>
        </section>
      </div>

      <div className="flex flex-wrap justify-end gap-2 border-t border-zinc-200 px-5 py-4">
        {!isTerminalIncident(incident) ? (
          <>
            {incident.status === "new" && (
              <Button
                variant="outline"
                className="border-zinc-300 bg-white text-zinc-800 hover:bg-zinc-100 hover:text-zinc-950"
                disabled={isAssigning}
                onClick={() => onAssignIncident(incident)}
              >
                {isAssigning ? <Loader2 className="size-4 animate-spin" /> : <PlayCircle className="size-4" />}
                {isAssigning ? "Назначаем..." : "Взять в работу"}
              </Button>
            )}
            <Button
              variant="outline"
              className="border-zinc-300 bg-white text-zinc-800 hover:bg-zinc-100 hover:text-zinc-950"
              disabled={incident.status === "new"}
              onClick={() => onCloseIncident(incident.id)}
            >
              <CheckCircle2 className="size-4" />Закрыть
            </Button>
          </>
        ) : (
          <>
            <Button
              variant="outline"
              className="border-zinc-300 bg-white text-zinc-800 hover:bg-zinc-100 hover:text-zinc-950"
              onClick={() => onReopenIncident(incident.id)}
            >
              <RefreshCcw className="size-4" />Переоткрыть
            </Button>
            <Button variant="outline" className="border-zinc-300 bg-white" onClick={() => onDownloadReport(incident.id)}>
              <Download className="size-4" />Скачать отчет
            </Button>
            <Button variant="outline" className="border-zinc-300 bg-white" onClick={() => onCreateRelated(incident.id)}>
              <Link2 className="size-4" />Создать связанный
            </Button>
          </>
        )}
        <Button className="bg-zinc-950 text-white hover:bg-zinc-800" onClick={() => onOpenDetails(incident.id)}>
          <ExternalLink className="size-4" />Открыть подробнее
        </Button>
      </div>
    </aside>
  )
}

const categoryOptions = [
  "Микроклимат",
  "Санитария",
  "Падеж и состояние стада",
  "Кормление",
  "Водоснабжение",
  "Производственные показатели",
  "Прочее",
]

const categoryBackendTypeMap: Record<string, string> = {
  "Микроклимат": "MICROCLIMATE",
  "Санитария": "SANITATION",
  "Падеж и состояние стада": "FLOCK_HEALTH",
  "Кормление": "FEEDING",
  "Водоснабжение": "WATER_SUPPLY",
  "Производственные показатели": "PRODUCTION_METRICS",
  "Прочее": "OTHER",
}

const priorityBackendMap: Record<string, string> = {
  "Критический": "CRITICAL",
  "Высокий": "HIGH",
  "Средний": "MEDIUM",
  "Низкий": "LOW",
}

const workshopOptions = ["Цех 1", "Цех 2", "Цех 3"]

const housesByWorkshop: Record<string, string[]> = {
  "Цех 1": ["Птичник 1", "Птичник 2", "Птичник 3"],
  "Цех 2": ["Птичник 1", "Птичник 2", "Птичник 3"],
  "Цех 3": ["Птичник 1", "Птичник 2", "Птичник 3"],
}

const zonesByHouse: Record<string, string[]> = {
  "Птичник 1": ["Линия поения 1", "Линия поения 2"],
  "Птичник 2": ["Линия поения 1", "Линия поения 2"],
  "Птичник 3": ["Линия поения 1", "Линия поения 2"],
}

const defaultResponsibleOptions = [
  "Главный ветеринарный врач",
  "Главный зоотехник",
  "Главный инженер",
  "Директор по качеству",
  "Системный администратор",
  "Руководитель комплекса",
  "Служба безопасности",
  "Оператор цеха (птичница)",
]

const responsibleByCategory: Record<string, string[]> = {
  "Микроклимат": ["Главный инженер", "Оператор цеха (птичница)", "Главный зоотехник"],
  "Санитария": ["Директор по качеству", "Служба безопасности", "Оператор цеха (птичница)"],
  "Падеж и состояние стада": ["Главный ветеринарный врач", "Главный зоотехник"],
  "Кормление": ["Главный зоотехник", "Руководитель комплекса"],
  "Водоснабжение": ["Главный инженер", "Оператор цеха (птичница)"],
  "Производственные показатели": ["Руководитель комплекса", "Главный зоотехник"],
  "Прочее": defaultResponsibleOptions,
}

const initialIncidents = sortIncidents(fallbackIncidents)

const analyticsTypeFilterMap: Record<string, string> = {
  MICROCLIMATE: "Микроклимат",
  SANITATION: "Санитария",
  FLOCK_HEALTH: "Падеж и состояние стада",
  FEEDING: "Кормление",
  WATER_SUPPLY: "Водоснабжение",
  PRODUCTION_METRICS: "Производственные показатели",

  LIGHTING_ILLUMINANCE_LOW: "Освещение",
  LIGHTING_ILLUMINANCE_HIGH: "Освещение",
  LIGHTING_UNIFORMITY_VIOLATION: "Освещение",
  LIGHTING_SYSTEM_HEALTH_WARNING: "Освещение",
  LIGHTING_SYSTEM_HEALTH_CRITICAL: "Освещение",
  LIGHTING_SCHEDULE_DEVIATION: "Освещение",
  LIGHTING_DARK_PERIOD_VIOLATION: "Освещение",
  LIGHTING_CONTROLLER_FAILURE: "Освещение",
  LIGHTING_CONTINUOUS_LIGHT: "Освещение",
  LIGHTING_CONTINUOUS_DARK: "Освещение",
  LIGHTING_MISSING_EVENTS: "Освещение",

  OTHER: "Прочее",
}

const analyticsPriorityFilterMap: Record<string, string> = {
  CRITICAL: "Критический",
  HIGH: "Высокий",
  MEDIUM: "Средний",
  LOW: "Низкий",
}

const analyticsStatusFilterMap: Record<string, string> = {
  OPEN: "Новый",
  IN_PROGRESS: "В работе",
  RESOLVED: "Решён",
  CLOSED: "Закрыт",
  CANCELLED: "Отменён",
}

interface IncidentsPageProps {
  selectedIncidentId?: string
}

export function IncidentsPage({ selectedIncidentId }: IncidentsPageProps = {}) {
  const sessionUser = useCurrentUser()
  const currentUser = { id: sessionUser?.id ?? "", name: sessionUser?.fullName ?? "" }
  const router = useRouter()
  const [incidents, setIncidents] = useState<Incident[]>(initialIncidents)
  const [activeIncidentId, setActiveIncidentId] = useState(
    selectedIncidentId ?? initialIncidents[0].id,
  )
  const [searchQuery, setSearchQuery] = useState("")
  const [periodDays, setPeriodDays] = useState<7 | 30>(7)
  const [workshopFilter, setWorkshopFilter] = useState("Все цеха")
  const [typeFilter, setTypeFilter] = useState("Все типы")
  const [houseFilter, setHouseFilter] = useState("Все птичники")
  const [statusFilter, setStatusFilter] = useState("Все статусы")
  const [priorityFilter, setPriorityFilter] = useState("Любой")
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isCreatingIncident, setIsCreatingIncident] = useState(false)
  const [isClosedExpanded, setIsClosedExpanded] = useState(false)
  const [createSuccessMessage, setCreateSuccessMessage] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [assigningIncidentId, setAssigningIncidentId] = useState<string | null>(null)
  const [selectedAssigneeRole, setSelectedAssigneeRole] = useState(enterpriseRoles[0])
  const [newCategory, setNewCategory] = useState(categoryOptions[0])
  const [newWorkshop, setNewWorkshop] = useState(workshopOptions[0])
  const [newHouse, setNewHouse] = useState(housesByWorkshop[workshopOptions[0]][0])
  const [newZone, setNewZone] = useState(zonesByHouse[housesByWorkshop[workshopOptions[0]][0]][0])
  const [newPriority, setNewPriority] = useState("Высокий")
  const [newDescription, setNewDescription] = useState("")
  const [newResponsible, setNewResponsible] = useState<string[]>(responsibleByCategory[categoryOptions[0]])
  const [newIncidentFiles, setNewIncidentFiles] = useState<File[]>([])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const requestedPeriod = Number(params.get("periodDays"))
    const requestedWorkshop = params.get("workshop")
    const requestedHouse = params.get("house")
    const requestedType = params.get("type")
    const requestedPriority = params.get("priority")
    const requestedStatus = params.get("status")

    setPeriodDays(requestedPeriod === 30 ? 30 : 7)
    if (requestedWorkshop) setWorkshopFilter(requestedWorkshop)
    if (requestedHouse) setHouseFilter(requestedHouse)
    if (requestedType) setTypeFilter(analyticsTypeFilterMap[requestedType] ?? requestedType)
    if (requestedPriority) setPriorityFilter(analyticsPriorityFilterMap[requestedPriority] ?? requestedPriority)
    if (requestedStatus) setStatusFilter(analyticsStatusFilterMap[requestedStatus] ?? requestedStatus)
  }, [])

  useEffect(() => {
    let isCancelled = false
    let hasCompletedInitialLoad = false

    async function loadIncidents() {
      if (!hasCompletedInitialLoad) {
        setIsLoading(true)
      }

      try {
        const response = await fetch("/api/incidents", { cache: "no-store" })
        if (!response.ok) throw new Error(`Backend returned ${response.status}`)

        const data = (await response.json()) as BackendIncident[]
        const nextIncidents = sortIncidents(data.map(toIncident))

        if (!isCancelled) {
          hasCompletedInitialLoad = true
          setIncidents(nextIncidents)
          setActiveIncidentId((currentId) =>
            nextIncidents.some((incident) => incident.id === currentId)
              ? currentId
              : nextIncidents[0]?.id ?? "",
          )
        }
      } catch {
        if (!isCancelled) {
          if (!hasCompletedInitialLoad) {
            setIncidents(initialIncidents)
            setActiveIncidentId((currentId) =>
              initialIncidents.some((incident) => incident.id === currentId)
                ? currentId
                : initialIncidents[0]?.id ?? "",
            )
          }

          hasCompletedInitialLoad = true
        }
      } finally {
        if (!isCancelled) setIsLoading(false)
      }
    }

    void loadIncidents()
    const intervalId = window.setInterval(() => {
      void loadIncidents()
    }, incidentRefreshIntervalMs)

    return () => {
      isCancelled = true
      window.clearInterval(intervalId)
    }
  }, [])

  const filteredIncidents = useMemo(() => {
    const query = searchQuery.trim().toLowerCase()
    const periodStart = Date.now() - periodDays * 24 * 60 * 60 * 1000

    return incidents.filter((incident) => {
      const timestamp = getIncidentTimestamp(incident)
      const matchesPeriod = timestamp !== undefined && timestamp >= periodStart
      const matchesSearch = !query || incident.id.toLowerCase().includes(query)
      const matchesWorkshop = workshopFilter === "Все цеха" || incident.workshop === workshopFilter
      const matchesType = typeFilter === "Все типы" || incident.type === typeFilter
      const matchesHouse = houseFilter === "Все птичники" || incident.poultryHouse === houseFilter
      const matchesStatus = statusFilter === "Все статусы" || statusConfig[incident.status].label === statusFilter
      const matchesPriority = priorityFilter === "Любой" || priorityConfig[incident.priority].label === priorityFilter
      return matchesPeriod && matchesSearch && matchesWorkshop && matchesType && matchesHouse && matchesStatus && matchesPriority
    })
  }, [incidents, periodDays, searchQuery, workshopFilter, typeFilter, houseFilter, statusFilter, priorityFilter])

  const activeFilteredIncidents = filteredIncidents.filter((incident) => !isTerminalIncident(incident))
  const closedFilteredIncidents = filteredIncidents.filter(isTerminalIncident)

  const activeIncident = filteredIncidents.find((incident) => incident.id === activeIncidentId) ?? filteredIncidents[0] ?? null

  const kpiItems = useMemo(() => buildKpiItems(filteredIncidents), [filteredIncidents])

  const typeOptions = useMemo(() => [
    "Все типы",
    ...Array.from(new Set([
      ...(typeFilter !== "Все типы" ? [typeFilter] : []),
      ...incidents.map((incident) => incident.type),
    ])),
  ], [incidents, typeFilter])
  const registryWorkshopOptions = useMemo(() => [
    "Все цеха",
    ...Array.from(new Set([
      ...(workshopFilter !== "Все цеха" ? [workshopFilter] : []),
      ...incidents.map((incident) => incident.workshop),
    ])),
  ], [incidents, workshopFilter])
  const houseOptions = useMemo(
    () => [
      "Все птичники",
      ...Array.from(new Set(
        [
          ...(houseFilter !== "Все птичники" ? [houseFilter] : []),
          ...incidents
            .filter((incident) => workshopFilter === "Все цеха" || incident.workshop === workshopFilter)
            .map((incident) => incident.poultryHouse),
        ],
      )),
    ],
    [houseFilter, incidents, workshopFilter],
  )
  const statusOptions = useMemo(() => [
    "Все статусы",
    ...Array.from(new Set([
      ...(statusFilter !== "Все статусы" ? [statusFilter] : []),
      ...incidents.map((incident) => statusConfig[incident.status].label),
    ])),
  ], [incidents, statusFilter])
  const priorityOptions = useMemo(() => [
    "Любой",
    ...Array.from(new Set([
      ...(priorityFilter !== "Любой" ? [priorityFilter] : []),
      ...incidents.map((incident) => priorityConfig[incident.priority].label),
    ])),
  ], [incidents, priorityFilter])

  const currentHouseOptions = useMemo(
    () => housesByWorkshop[newWorkshop] ?? [],
    [newWorkshop],
  )
  const currentZoneOptions = useMemo(
    () => zonesByHouse[newHouse] ?? [],
    [newHouse],
  )
  const currentResponsibleOptions = useMemo(
    () => responsibleByCategory[newCategory] ?? defaultResponsibleOptions,
    [newCategory],
  )

  useEffect(() => {
    setNewResponsible(currentResponsibleOptions)
  }, [currentResponsibleOptions])

  useEffect(() => {
    const firstHouse = currentHouseOptions[0]
    if (firstHouse && !currentHouseOptions.includes(newHouse)) {
      setNewHouse(firstHouse)
    }
  }, [newWorkshop, currentHouseOptions, newHouse])

  useEffect(() => {
    const firstZone = currentZoneOptions[0]
    if (firstZone && !currentZoneOptions.includes(newZone)) {
      setNewZone(firstZone)
    }
  }, [newHouse, currentZoneOptions, newZone])

  const handleCreateOpenChange = (open: boolean) => {
    if (isCreatingIncident) {
      return
    }

    setIsCreateOpen(open)

    if (!open) {
      setNewIncidentFiles([])
    }
  }

  const handleCreateIncident = async () => {
    if (isCreatingIncident) {
      return
    }

    setIsCreatingIncident(true)

    try {
      const response = await fetch("/api/incidents", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          type: categoryBackendTypeMap[newCategory] ?? "OTHER",
          workshop: newWorkshop,
          house: newHouse,
          zone: newZone,
          priority: priorityBackendMap[newPriority] ?? "MEDIUM",
          description: newDescription.trim(),
          responsible: newResponsible.join(", ") || null,
        }),
      })
      const responseBody = await response.text()

      if (!response.ok) {
        throw new Error(responseBody || "Не удалось создать инцидент")
      }

      const createdIncident = JSON.parse(responseBody) as BackendIncident
      const nextIncident = toIncident(createdIncident)
      let attachmentMessage = ""

      if (newIncidentFiles.length > 0) {
        try {
          await uploadIncidentAttachments(createdIncident.id, newIncidentFiles)
          attachmentMessage = `. Материалы: ${newIncidentFiles.length}`
        } catch (uploadError) {
          const uploadMessage =
            uploadError instanceof Error ? uploadError.message : "неизвестная ошибка"
          attachmentMessage = `. Материалы не загружены: ${uploadMessage}`
        }
      }

      setIncidents((current) => sortIncidents([nextIncident, ...current]))
      setActiveIncidentId(nextIncident.id)
      setIsCreateOpen(false)
      setNewDescription("")
      setNewIncidentFiles([])
      setCreateSuccessMessage(`Инцидент №${nextIncident.id} успешно создан и передан в работу${attachmentMessage}`)
      setTimeout(() => setCreateSuccessMessage(""), 4000)
    } catch (error) {
      setCreateSuccessMessage(error instanceof Error ? error.message : "Не удалось создать инцидент")
      setTimeout(() => setCreateSuccessMessage(""), 4000)
    } finally {
      setIsCreatingIncident(false)
    }
  }

  const updateIncidentAfterAssign = (id: string, assignedIncident: Partial<Incident>) => {
    setIncidents((current) =>
      current.map((incident) =>
        incident.id === id
          ? {
              ...incident,
              ...assignedIncident,
              status: "inProgress",
              responsible: assignedIncident.responsible ?? currentUser.name,
              assigneeRole: assignedIncident.assigneeRole ?? selectedAssigneeRole,
              comment: "Инцидент взят в работу. Ответственный назначен.",
            }
          : incident,
      ),
    )
  }

  const handleAssignIncident = async (incident: Incident) => {
    if (incident.status !== "new" || !sessionUser) return

    setAssigningIncidentId(incident.id)

    try {
      if (incident.backendId) {
        const response = await fetch(`/api/incidents/${incident.backendId}/assign`, {
          method: "PATCH",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            userId: currentUser.id,
            userName: currentUser.name,
            role: selectedAssigneeRole,
          }),
        })

        const rawBody = await response.text()

        if (response.status === 409) {
          let message = `Этот инцидент уже взят в работу пользователем ${incident.responsible}`
          try {
            const conflict = JSON.parse(rawBody) as { message?: string; detail?: string }
            message = conflict.message ?? conflict.detail ?? message
          } catch {
            message = rawBody || message
          }
          setCreateSuccessMessage(message)
          updateIncidentAfterAssign(incident.id, {
            responsible: message.replace("Этот инцидент уже взят в работу пользователем ", "") || incident.responsible,
            assigneeRole: incident.assigneeRole,
          })
          return
        }

        if (!response.ok) {
          throw new Error(rawBody || "Не удалось назначить инцидент")
        }

        const updatedBackendIncident = rawBody ? (JSON.parse(rawBody) as BackendIncident) : null
        const updatedIncident = updatedBackendIncident ? toIncident(updatedBackendIncident) : null
        updateIncidentAfterAssign(incident.id, updatedIncident ?? {})
      } else {
        updateIncidentAfterAssign(incident.id, {
          responsible: currentUser.name,
          assigneeRole: selectedAssigneeRole,
          startedAt: new Date().toISOString(),
        })
      }

      setCreateSuccessMessage(`Инцидент ${incident.id} назначен на вас`)
    } catch {
      setCreateSuccessMessage("Ошибка соединения: инцидент не назначен, попробуйте еще раз.")
    } finally {
      setAssigningIncidentId(null)
      setTimeout(() => setCreateSuccessMessage(""), 4000)
    }
  }

  const handleCloseIncident = async (id: string) => {
  const incident = incidents.find((i) => i.id === id)
  if (!incident?.backendId) return

  try {
    const response = await fetch(`/api/incidents/${incident.backendId}/status`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify("CLOSED"),
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(errorText || "Не удалось закрыть инцидент")
    }

    const updated = (await response.json()) as BackendIncident
    const nextIncident = toIncident(updated)

    setIncidents((current) =>
      current.map((i) => (i.id === id ? nextIncident : i))
    )
    setIsClosedExpanded(true)
    setCreateSuccessMessage(`Инцидент ${id} закрыт`)
    setTimeout(() => setCreateSuccessMessage(""), 3000)
  } catch (error) {
    setCreateSuccessMessage(
      error instanceof Error ? error.message : "Ошибка закрытия инцидента"
    )
    setTimeout(() => setCreateSuccessMessage(""), 4000)
  }
}
const handleReopenIncident = async (id: string) => {
  const incident = incidents.find((i) => i.id === id)
  if (!incident?.backendId) return

  try {
    const response = await fetch(`/api/incidents/${incident.backendId}/status`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify("IN_PROGRESS"),
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(errorText || "Не удалось переоткрыть инцидент")
    }

    const updated = (await response.json()) as BackendIncident
    const nextIncident = toIncident(updated)

    setIncidents((current) =>
      current.map((i) => (i.id === id ? nextIncident : i))
    )
    setCreateSuccessMessage(`Инцидент ${id} переоткрыт`)
    setTimeout(() => setCreateSuccessMessage(""), 3000)
  } catch (error) {
    setCreateSuccessMessage(
      error instanceof Error ? error.message : "Ошибка переоткрытия"
    )
    setTimeout(() => setCreateSuccessMessage(""), 4000)
  }
}

  const handleDownloadReport = (id: string) => {
    setCreateSuccessMessage(`Сформирован отчет по инциденту ${id} (демо).`)
    setTimeout(() => setCreateSuccessMessage(""), 3000)
  }

  const handleCreateRelated = (id: string) => {
    const sourceIncident = incidents.find((incident) => incident.id === id)
    if (!sourceIncident) return

    setNewCategory(sourceIncident.type)
    setNewWorkshop(sourceIncident.workshop)
    setNewHouse(sourceIncident.poultryHouse)
    setNewDescription(`Связан с ${id}: продолжение кейса.`)
    setIsCreateOpen(true)
  }

  return (
    <main className="flex min-h-0 flex-1 flex-col rounded-[28px] bg-background">
      <div className="border-b border-zinc-200 px-6 py-4">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">Реестр инцидентов</h1>
            <p className="mt-2 text-sm text-zinc-500">Контроль отклонений, ответственных и статусов выполнения</p>
            <div className="mt-2 flex flex-wrap items-center gap-3 text-sm text-zinc-500">
              {isLoading && <span>Загрузка из бэкенда...</span>}
            </div>
          </div>

          <div className="flex w-full flex-col gap-3 sm:flex-row xl:w-auto">
            <div className="relative w-full sm:w-[360px]">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-zinc-500" />
              <Input
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="Поиск по номеру инцидента (INC-...)"
                className="h-10 border-zinc-300 bg-white pl-9 text-zinc-900 placeholder:text-zinc-500"
              />
            </div>
            <Button onClick={() => setIsCreateOpen(true)} className="bg-red-600 text-white hover:bg-red-700">
              <ClipboardPlus className="size-4" />Создать инцидент
            </Button>
          </div>
        </div>
      </div>
      {createSuccessMessage && (
        <div className="fixed bottom-5 right-5 z-50 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 shadow-md">
          {createSuccessMessage}
        </div>
      )}

      <section className="grid gap-3 border-b border-zinc-200 px-6 py-4 sm:grid-cols-2 xl:grid-cols-4">
        {kpiItems.map((item) => {
          const Icon = item.icon
          return (
            <div key={item.label} className="rounded-lg border border-zinc-200 bg-white p-4 shadow-sm">
              <div className="flex items-center justify-between gap-3">
                <p className="text-xs uppercase tracking-wide text-zinc-500">{item.label}</p>
                <Icon className={cn("size-4", item.tone)} />
              </div>
              <p className={cn("mt-2 text-3xl font-bold", item.tone)}>{item.value}</p>
            </div>
          )
        })}
      </section>

      <section className="border-b border-zinc-200 px-6 py-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
          <FieldSelect
            label="Период"
            options={["7 дней", "30 дней"]}
            value={`${periodDays} дней`}
            onValueChange={(value) => setPeriodDays(value === "30 дней" ? 30 : 7)}
          />
          <FieldSelect
            label="Цех"
            options={registryWorkshopOptions}
            value={workshopFilter}
            onValueChange={(value) => {
              setWorkshopFilter(value)
              setHouseFilter("Все птичники")
            }}
          />
          <FieldSelect label="Тип" options={typeOptions} value={typeFilter} onValueChange={setTypeFilter} />
          <FieldSelect label="Птичник" options={houseOptions} value={houseFilter} onValueChange={setHouseFilter} />
          <FieldSelect label="Статус" options={statusOptions} value={statusFilter} onValueChange={setStatusFilter} />
          <FieldSelect label="Приоритет" options={priorityOptions} value={priorityFilter} onValueChange={setPriorityFilter} />
        </div>
      </section>

      <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[minmax(0,1fr)_420px]">
        <section className="min-h-0 overflow-y-auto px-6 py-5">
          <div className="mb-4 flex items-center justify-between gap-3">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-700">Таблица инцидентов</h2>
            <span className="text-sm text-zinc-500">Найдено: {activeFilteredIncidents.length}</span>
          </div>

          <div className="overflow-hidden rounded-lg border border-zinc-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[1060px] text-left text-sm">
                <thead className="border-b border-zinc-200 bg-zinc-50 text-xs uppercase tracking-wide text-zinc-500">
                  <tr>
                    <th className="px-4 py-3 font-medium">№</th>
                    <th className="px-4 py-3 font-medium">Дата</th>
                    <th className="px-4 py-3 font-medium">Тип</th>
                    <th className="px-4 py-3 font-medium">Краткое описание</th>
                    <th className="px-4 py-3 font-medium">Цех</th>
                    <th className="px-4 py-3 font-medium">Птичник</th>
                    <th className="px-4 py-3 font-medium">Приоритет</th>
                    <th className="px-4 py-3 font-medium">Статус</th>
                    <th className="px-4 py-3 font-medium">Ответственный</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-200">
                  {activeFilteredIncidents.length === 0 ? (
                    <tr>
                      <td colSpan={9} className="px-4 py-8 text-center text-sm text-zinc-500">Инцидентов по фильтрам нет.</td>
                    </tr>
                  ) : (
                    activeFilteredIncidents.map((incident) => {
                      const Icon = incident.icon
                      const isActive = incident.id === activeIncident?.id

                      return (
                        <tr
                          key={incident.id}
                          tabIndex={0}
                          onClick={() => setActiveIncidentId(incident.id)}
                          onKeyDown={(event) => {
                            if (event.key === "Enter" || event.key === " ") setActiveIncidentId(incident.id)
                          }}
                          className={cn(
                            "cursor-pointer border-l-4 transition-colors outline-none",
                            priorityConfig[incident.priority].rowClass,
                            isActive ? "bg-zinc-100" : "hover:bg-zinc-50",
                          )}
                        >
                          <td className="px-4 py-3 font-medium text-zinc-900">{incident.id}</td>
                          <td className="px-4 py-3 text-zinc-600">{incident.date} {incident.time}</td>
                          <td className="px-4 py-3"><span className="flex items-center gap-2 text-zinc-800"><Icon className={cn("size-4", priorityConfig[incident.priority].iconClass)} />{incident.type}</span></td>
                          <td className="px-4 py-3 text-zinc-700">{incident.shortDescription}</td>
                          <td className="px-4 py-3 text-zinc-600">{incident.workshop}</td>
                          <td className="px-4 py-3 text-zinc-600">{incident.poultryHouse}</td>
                          <td className="px-4 py-3"><Badge className={priorityConfig[incident.priority].className}>{priorityConfig[incident.priority].label}</Badge></td>
                          <td className="px-4 py-3"><Badge className={statusConfig[incident.status].className}>{statusConfig[incident.status].label}</Badge></td>
                          <td className="px-4 py-3 text-zinc-700">{incident.responsible}</td>
                        </tr>
                      )
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className="mt-5 lg:hidden">
            {activeIncident ? <IncidentDetails incident={activeIncident} assigningIncidentId={assigningIncidentId} selectedAssigneeRole={selectedAssigneeRole} onOpenDetails={(id) => router.push(`/incidents/${id}`)} onAssignIncident={handleAssignIncident} onAssigneeRoleChange={setSelectedAssigneeRole} onCloseIncident={handleCloseIncident} onReopenIncident={handleReopenIncident} onDownloadReport={handleDownloadReport} onCreateRelated={handleCreateRelated} /> : <div className="rounded-lg border border-zinc-200 bg-white p-5 text-sm text-zinc-500">Инцидентов нет.</div>}
          </div>

          <div className="mt-6 overflow-hidden rounded-lg border border-zinc-200 bg-white shadow-sm">
            <button
              type="button"
              onClick={() => setIsClosedExpanded((value) => !value)}
              className="flex w-full items-center justify-between border-b border-zinc-200 bg-zinc-50 px-4 py-3 text-left text-sm font-semibold uppercase tracking-wide text-zinc-700"
            >
              <span>Завершённые инциденты ({closedFilteredIncidents.length})</span>
              {isClosedExpanded ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
            </button>

            {isClosedExpanded && (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[980px] text-left text-sm">
                  <thead className="border-b border-zinc-200 bg-white text-xs uppercase tracking-wide text-zinc-500">
                    <tr>
                      <th className="px-4 py-3 font-medium">№</th>
                      <th className="px-4 py-3 font-medium">Дата</th>
                      <th className="px-4 py-3 font-medium">Тип</th>
                      <th className="px-4 py-3 font-medium">Цех</th>
                      <th className="px-4 py-3 font-medium">Птичник</th>
                      <th className="px-4 py-3 font-medium">Ответственный</th>
                      <th className="px-4 py-3 font-medium">Действие</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-200">
                    {closedFilteredIncidents.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="px-4 py-6 text-center text-sm text-zinc-500">
                          Закрытых инцидентов по фильтрам нет.
                        </td>
                      </tr>
                    ) : (
                      closedFilteredIncidents.map((incident) => (
                        <tr key={`closed-${incident.id}`} className="hover:bg-zinc-50">
                          <td className="px-4 py-3 font-medium text-zinc-900">{incident.id}</td>
                          <td className="px-4 py-3 text-zinc-600">{incident.date} {incident.time}</td>
                          <td className="px-4 py-3 text-zinc-700">{incident.type}</td>
                          <td className="px-4 py-3 text-zinc-600">{incident.workshop}</td>
                          <td className="px-4 py-3 text-zinc-600">{incident.poultryHouse}</td>
                          <td className="px-4 py-3 text-zinc-700">{incident.responsible}</td>
                          <td className="px-4 py-3">
                            <Button variant="outline" size="sm" onClick={() => handleReopenIncident(incident.id)}>
                              Переоткрыть
                            </Button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </section>

        <div className="hidden min-h-0 lg:block">
          {activeIncident ? (
            <IncidentDetails incident={activeIncident} assigningIncidentId={assigningIncidentId} selectedAssigneeRole={selectedAssigneeRole} onOpenDetails={(id) => router.push(`/incidents/${id}`)} onAssignIncident={handleAssignIncident} onAssigneeRoleChange={setSelectedAssigneeRole} onCloseIncident={handleCloseIncident} onReopenIncident={handleReopenIncident} onDownloadReport={handleDownloadReport} onCreateRelated={handleCreateRelated} />
          ) : (
            <aside className="flex h-full items-center justify-center rounded-br-[28px] border-l border-zinc-200 bg-white p-5 text-sm text-zinc-500">Инцидентов нет.</aside>
          )}
        </div>
      </div>

      <Sheet open={isCreateOpen} onOpenChange={handleCreateOpenChange}>
        <SheetContent side="right" className="w-full sm:max-w-[460px]">
          <SheetHeader>
            <SheetTitle>Создание инцидента</SheetTitle>
            <SheetDescription>Заполните форму и нажмите «Создать»</SheetDescription>
          </SheetHeader>

          <div className="space-y-4 overflow-y-auto px-4 pb-4">
            <label className="block space-y-1">
              <span className="text-xs uppercase tracking-wide text-zinc-500">Категория</span>
              <select value={newCategory} onChange={(event) => setNewCategory(event.target.value)} className="h-9 w-full rounded-md border border-zinc-300 bg-white px-3 text-sm">
                {categoryOptions.map((option) => (
                  <option key={option}>{option}</option>
                ))}
              </select>
            </label>

            <label className="block space-y-1">
              <span className="text-xs uppercase tracking-wide text-zinc-500">Цех</span>
              <select value={newWorkshop} onChange={(event) => setNewWorkshop(event.target.value)} className="h-9 w-full rounded-md border border-zinc-300 bg-white px-3 text-sm">
                {workshopOptions.map((option) => (
                  <option key={option}>{option}</option>
                ))}
              </select>
            </label>

            <label className="block space-y-1">
              <span className="text-xs uppercase tracking-wide text-zinc-500">Птичник</span>
              <select value={newHouse} onChange={(event) => setNewHouse(event.target.value)} className="h-9 w-full rounded-md border border-zinc-300 bg-white px-3 text-sm">
                {currentHouseOptions.map((option) => (
                  <option key={option}>{option}</option>
                ))}
              </select>
            </label>

            <label className="block space-y-1">
              <span className="text-xs uppercase tracking-wide text-zinc-500">Зона</span>
              <select value={newZone} onChange={(event) => setNewZone(event.target.value)} className="h-9 w-full rounded-md border border-zinc-300 bg-white px-3 text-sm">
                {currentZoneOptions.map((option) => (
                  <option key={option}>{option}</option>
                ))}
              </select>
            </label>

            <label className="block space-y-1">
              <span className="text-xs uppercase tracking-wide text-zinc-500">Приоритет</span>
              <select value={newPriority} onChange={(event) => setNewPriority(event.target.value)} className="h-9 w-full rounded-md border border-zinc-300 bg-white px-3 text-sm">
                {["Низкий", "Средний", "Высокий", "Критический"].map((option) => (
                  <option key={option}>{option}</option>
                ))}
              </select>
            </label>

            <label className="block space-y-1">
              <span className="text-xs uppercase tracking-wide text-zinc-500">Описание</span>
              <Textarea value={newDescription} onChange={(event) => setNewDescription(event.target.value)} placeholder="Опишите, что произошло..." />
            </label>

            <label className="block space-y-1">
              <span className="text-xs uppercase tracking-wide text-zinc-500">Ответственный</span>
              <select
                multiple
                value={newResponsible}
                onChange={(event) =>
                  setNewResponsible(Array.from(event.target.selectedOptions).map((option) => option.value))
                }
                className="min-h-24 w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm"
              >
                {currentResponsibleOptions.map((option) => (
                  <option key={option}>{option}</option>
                ))}
              </select>
            </label>

            <IncidentAttachmentPicker
              files={newIncidentFiles}
              onFilesChange={setNewIncidentFiles}
              disabled={isCreatingIncident}
            />
          </div>

          <SheetFooter>
            <Button
              variant="outline"
              disabled={isCreatingIncident}
              onClick={() => handleCreateOpenChange(false)}
            >
              Отмена
            </Button>
            <Button
              disabled={isCreatingIncident}
              onClick={handleCreateIncident}
              className="bg-zinc-950 text-white hover:bg-zinc-800"
            >
              {isCreatingIncident ? "Создание..." : "Создать"}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </main>
  )
}