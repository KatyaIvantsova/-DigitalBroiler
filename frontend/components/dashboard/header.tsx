"use client"

import Link from "next/link"
import { ChevronDown, ChevronRight, Clock3, LogOut } from "lucide-react"
import { useSyncExternalStore } from "react"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { logout, useCurrentUser } from "@/hooks/use-current-user"
import { ROLE_LABELS } from "@/lib/auth"
import { visibleNavItems } from "@/components/app/app-shell"

function initials(fullName: string): string {
  return fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("")
}

export type DashboardSection = "technical" | "incidents" | "analytics" | "notifications" | "tasks"

interface DashboardHeaderProps {
  activeSection: DashboardSection
  onSectionChange: (section: DashboardSection) => void
}

const CLOCK_FORMAT = new Intl.DateTimeFormat("ru-RU", { dateStyle: "medium", timeStyle: "short" })

function currentTimestamp() {
  return CLOCK_FORMAT.format(new Date())
}

function subscribeToClock(onChange: () => void) {
  const timer = window.setInterval(onChange, 30_000)
  return () => window.clearInterval(timer)
}

export function DashboardHeader({
  activeSection,
  onSectionChange,
}: DashboardHeaderProps) {
  const user = useCurrentUser()
  // Время только в браузере пользователя: сервер может жить в другом часовом поясе (ошибка гидратации React #418)
  const timestamp = useSyncExternalStore(subscribeToClock, currentTimestamp, () => "")

  return (
    <header className="border-b border-black/5 px-4 py-4 dark:border-white/8 md:px-6">
      <div className="flex flex-nowrap items-center justify-between gap-6 overflow-x-auto">
        <div className="min-w-0 flex-1">
          <nav className="flex flex-nowrap items-center gap-1.5 overflow-hidden text-sm text-zinc-500 dark:text-zinc-400">
            <span className="dashboard-chip">АгроКонтроль</span>
            <ChevronRight className="size-4 opacity-50" />
            <span className="truncate">Ситуационный центр</span>
            <ChevronRight className="size-4 opacity-50" />
            <span className="truncate font-medium text-zinc-900 dark:text-zinc-100">
              {user ? user.position : "…"}
            </span>
          </nav>
        </div>

        <div className="flex shrink-0 items-center gap-3">
          <Link href="/flocks" className="dashboard-chip whitespace-nowrap hover:bg-white dark:hover:bg-white/10">
            Партии
          </Link>
          <Tabs
            value={activeSection}
            onValueChange={(value) => onSectionChange(value as DashboardSection)}
            className="hidden md:block"
          >
            <TabsList className="bg-background border border-zinc-800">
              <TabsTrigger
                value="technical"
                className="data-[state=active]:bg-zinc-700 data-[state=active]:text-white text-zinc-400"
              >
                Технические показатели
              </TabsTrigger>
              <TabsTrigger
                value="incidents"
                className="data-[state=active]:bg-zinc-700 data-[state=active]:text-white text-zinc-400"
              >
                Реестр инцидентов
              </TabsTrigger>
              <TabsTrigger
                value="analytics"
                className="data-[state=active]:bg-zinc-700 data-[state=active]:text-white text-zinc-400"
              >
                Аналитика
              </TabsTrigger>
              <TabsTrigger
                value="notifications"
                className="data-[state=active]:bg-zinc-700 data-[state=active]:text-white text-zinc-400"
              >
                Уведомления
              </TabsTrigger>
              <TabsTrigger
                value="tasks"
                className="data-[state=active]:bg-zinc-700 data-[state=active]:text-white text-zinc-400"
              >
                Задачи
              </TabsTrigger>
            </TabsList>
          </Tabs>

          <div className="flex shrink-0 items-center gap-3">
            <div className="dashboard-chip whitespace-nowrap">
              <span className="relative flex size-2.5">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-70" />
                <span className="relative inline-flex size-2.5 rounded-full bg-emerald-500" />
              </span>
              Реальное время
            </div>

            <div className="dashboard-chip hidden whitespace-nowrap md:inline-flex">
              <Clock3 className="size-3.5" />
              {timestamp}
            </div>

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button className="flex items-center gap-3 rounded-full border border-black/5 bg-white/80 px-2 py-2 text-left shadow-sm transition hover:bg-white dark:border-white/10 dark:bg-white/6 dark:hover:bg-white/10">
                  <Avatar className="size-9 border border-black/5 dark:border-white/10">
                    <AvatarFallback className="bg-zinc-900 text-white dark:bg-white dark:text-zinc-900">
                      {user ? initials(user.fullName) : ""}
                    </AvatarFallback>
                  </Avatar>
                  <div className="hidden min-w-0 sm:block">
                    <div className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                      {user?.fullName ?? "Загрузка…"}
                    </div>
                    <div className="text-xs text-zinc-500 dark:text-zinc-400">
                      {user ? ROLE_LABELS[user.role] : ""}
                    </div>
                  </div>
                  <ChevronDown className="hidden size-4 text-zinc-400 sm:block" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="border-black/5 bg-white/95 dark:border-white/10 dark:bg-zinc-900/95">
                {visibleNavItems(user?.role)
                  .filter((item) => item.href !== "/")
                  .map((item) => (
                    <DropdownMenuItem key={item.href} asChild>
                      <Link href={item.href}>{item.label}</Link>
                    </DropdownMenuItem>
                  ))}
                <DropdownMenuItem onSelect={() => void logout()}>
                  <LogOut className="mr-2 size-4" />
                  Выйти
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </div>
    </header>
  )
}
