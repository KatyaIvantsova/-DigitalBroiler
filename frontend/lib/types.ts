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
