import type { CurrentUser } from "@/lib/auth"

export type Role = CurrentUser["role"]

export type Zone = { id: string; houseId: string; code: string; name: string; layout: string | null }

export type House = {
  id: string
  siteId: string
  siteName: string | null
  code: string
  name: string
  areaM2: number | null
  capacityHeads: number | null
  active: boolean
  zones: Zone[]
  activeFlockId: string | null
}

export type Site = { id: string; code: string; name: string; address: string | null; timezone: string }

export type Breed = { code: string; name: string }

export type FlockStatus = "PLANNED" | "ACTIVE" | "CLOSED"

export type Flock = {
  id: string
  houseId: string
  houseName: string | null
  code: string
  breedCode: string
  breedName: string
  placedAt: string
  placedHeads: number
  placedAvgWeightG: number | null
  sex: "MIXED" | "MALE" | "FEMALE"
  hatchery: string | null
  targetAgeDays: number | null
  status: FlockStatus
  closedAt: string | null
  shippedHeads: number | null
  shippedLiveWeightKg: number | null
  ageDays: number | null
  mortalityTotal: number
  culledTotal: number
  currentHeads: number
}

export type DailyRecord = {
  id: string
  flockId: string
  recordDate: string
  ageDay: number
  mortalityHeads: number
  culledHeads: number
  feedConsumedKg: number | null
  waterConsumedL: number | null
  comment: string | null
  headsAtEnd: number
}

export type Weighing = {
  id: string
  flockId: string
  zoneId: string | null
  weighedAt: string
  ageDay: number
  sampleHeads: number
  avgWeightG: number
  uniformityPct: number | null
  method: "MANUAL" | "SCALE" | "VIDEO"
}

export type AuditEntry = {
  id: string
  entityType: string
  entityId: string
  action: string
  actorId: string | null
  actorName: string | null
  summary: string
  changes: string | null
  createdAt: string
}

export type AdminUser = {
  id: string
  username: string | null
  fullName: string
  position: string
  role: Role
  enabled: boolean
  canLogin: boolean
  houseIds: string[]
  createdAt: string
}

export type Sensor = {
  id: string
  code: string
  name: string
  type: string
  farm: string
  building: string
  unit: string
  active: boolean
  houseId: string | null
  zoneId: string | null
}

export const FLOCK_STATUS_LABELS: Record<FlockStatus, string> = {
  PLANNED: "Запланирована",
  ACTIVE: "Активна",
  CLOSED: "Закрыта",
}

export const SEX_LABELS: Record<Flock["sex"], string> = {
  MIXED: "Смешанная",
  MALE: "Петушки",
  FEMALE: "Курочки",
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return "—"
  const date = value.length === 10 ? new Date(`${value}T00:00:00`) : new Date(value)
  return new Intl.DateTimeFormat("ru-RU", { dateStyle: "short" }).format(date)
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—"
  return new Intl.DateTimeFormat("ru-RU", { dateStyle: "short", timeStyle: "short" }).format(new Date(value))
}

export function formatNumber(value: number | null | undefined, digits = 0): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—"
  return new Intl.NumberFormat("ru-RU", { maximumFractionDigits: digits, minimumFractionDigits: digits }).format(value)
}

export function todayIso(): string {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

export type NormMetric =
  | "TEMPERATURE"
  | "HUMIDITY"
  | "CO2"
  | "AMMONIA"
  | "LIGHT_INTENSITY"
  | "LIGHT_HOURS"
  | "BODY_WEIGHT"
  | "FEED_INTAKE"
  | "WATER_INTAKE"
  | "FCR"
  | "MORTALITY"
  | "UNIFORMITY"

export const NORM_METRIC_LABELS: Record<NormMetric, string> = {
  TEMPERATURE: "Температура",
  HUMIDITY: "Влажность",
  CO2: "CO2",
  AMMONIA: "Аммиак",
  LIGHT_INTENSITY: "Освещённость",
  LIGHT_HOURS: "Световой день",
  BODY_WEIGHT: "Живая масса",
  FEED_INTAKE: "Потребление корма",
  WATER_INTAKE: "Потребление воды",
  FCR: "Конверсия корма (FCR)",
  MORTALITY: "Падёж за сутки",
  UNIFORMITY: "Однородность",
}

export type Norm = {
  id: string
  breedCode: string | null
  metric: NormMetric
  ageFromDay: number
  ageToDay: number
  minValue: number | null
  targetValue: number | null
  maxValue: number | null
  unit: string
  source: string
  version: number
  previousId: string | null
  validFrom: string
  validTo: string | null
  changedBy: string | null
  changeComment: string | null
}

export type Rule = {
  code: string
  name: string
  metric: NormMetric | null
  sensorType: string | null
  incidentType: string
  warnMinutes: number
  warnDelta: number | null
  criticalDelta: number | null
  criticalMinutes: number
  clearMinutes: number
  enabled: boolean
}

// KPI партии (S4-01): null — показатель не считается, нет данных
export type FlockKpi = {
  flockId: string
  closed: boolean
  ageDays: number | null
  placedHeads: number
  mortality: number
  culled: number
  heads: number
  unaccountedHeads: number | null
  survivalPct: number
  lossPct: number
  avgWeightG: number | null
  weightDate: string | null
  weightAgeDays: number | null
  feedKg: number | null
  feedMissingDays: number
  liveMassKg: number | null
  fcr: number | null
  adgG: number | null
  epef: number | null
  normWeightG: number | null
  weightDeviationPct: number | null
  normFcr: number | null
  fcrDeviation: number | null
}

// План-факт по кривой кросса (S4-02)
export type PlanFactPoint = {
  date: string
  ageDay: number
  weightG: number
  uniformityPct: number | null
  normWeightG: number | null
  normWeightMinG: number | null
  weightDeviationG: number | null
  weightDeviationPct: number | null
  fcr: number | null
  normFcr: number | null
  fcrDeviation: number | null
}

export type PlanFact = {
  breedCode: string
  weighings: PlanFactPoint[]
  curve: { ageDay: number; weightG: number | null; fcr: number | null }[]
}

export type FlockImportResult = {
  applied: boolean
  rows: number
  created: number
  updated: number
  unchanged: number
  weighings: number
  errors: string[]
}

export type IncidentPriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"

// Инцидент в объёме, нужном главной и экрану партии
export type IncidentSummary = {
  id: string
  code: string
  title: string
  description: string | null
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED" | string
  priority: IncidentPriority
  type: string
  house: string | null
  houseId: string | null
  flockId: string | null
  createdAt: string
}

export type SensorReading = {
  sensorId: string
  sensorCode: string
  type: string
  value: number
  unit: string
  measuredAt: string
}

export function isOpenIncident(incident: IncidentSummary): boolean {
  return incident.status === "OPEN" || incident.status === "IN_PROGRESS"
}
