"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import type { ReactNode } from "react"
import { LogOut } from "lucide-react"
import { logout, useCurrentUser } from "@/hooks/use-current-user"
import { ROLE_LABELS } from "@/lib/auth"
import type { Role } from "@/lib/types"
import { cn } from "@/lib/utils"

type NavItem = { href: string; label: string; roles?: Role[] }

export const NAV_ITEMS: NavItem[] = [
  { href: "/", label: "Ситуационный центр" },
  { href: "/flocks", label: "Партии" },
  { href: "/norms", label: "Нормы и правила" },
  { href: "/admin/users", label: "Пользователи", roles: ["ADMIN"] },
  { href: "/admin/structure", label: "Птичники и датчики", roles: ["ADMIN"] },
  { href: "/admin/audit", label: "Журнал действий", roles: ["TECHNOLOGIST", "MANAGER", "ADMIN"] },
]

export function visibleNavItems(role: Role | undefined) {
  return NAV_ITEMS.filter((item) => !item.roles || (role && item.roles.includes(role)))
}

// Каркас страниц, вынесенных из главного дашборда: навигация, пользователь, выход.
export function AppShell({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
  const user = useCurrentUser()
  const pathname = usePathname()

  return (
    <div className="min-h-screen px-3 py-3 md:px-5 md:py-5">
      <div className="dashboard-shell mx-auto max-w-[1680px] rounded-[28px]">
        <header className="flex flex-wrap items-center justify-between gap-3 border-b border-black/5 px-4 py-4 dark:border-white/8 md:px-6">
          <nav className="flex flex-wrap items-center gap-1.5">
            <span className="dashboard-chip mr-2">АгроКонтроль</span>
            {visibleNavItems(user?.role).map((item) => {
              const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href)
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    "rounded-full px-3 py-1.5 text-sm transition",
                    active
                      ? "bg-zinc-900 text-white dark:bg-white dark:text-zinc-900"
                      : "text-zinc-600 hover:bg-black/5 dark:text-zinc-300 dark:hover:bg-white/10",
                  )}
                >
                  {item.label}
                </Link>
              )
            })}
          </nav>
          <div className="flex items-center gap-3 text-sm">
            {user && (
              <span className="text-zinc-600 dark:text-zinc-300">
                {user.fullName} · {ROLE_LABELS[user.role]}
              </span>
            )}
            <button
              type="button"
              onClick={() => void logout()}
              className="inline-flex items-center gap-1 rounded-full px-3 py-1.5 text-zinc-600 hover:bg-black/5 dark:text-zinc-300 dark:hover:bg-white/10"
            >
              <LogOut className="size-4" /> Выйти
            </button>
          </div>
        </header>
        <main className="space-y-4 p-3 md:p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h1 className="text-xl font-semibold text-zinc-900 dark:text-zinc-50">{title}</h1>
            {actions}
          </div>
          {children}
        </main>
      </div>
    </div>
  )
}

export function Panel({ title, actions, children, className }: { title?: string; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={cn("dashboard-panel p-4 md:p-5", className)}>
      {(title || actions) && (
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          {title && <h2 className="text-base font-semibold text-zinc-900 dark:text-zinc-50">{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  )
}

export function ErrorNote({ message }: { message?: string }) {
  if (!message) return null
  return (
    <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-300">
      {message}
    </div>
  )
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: ReactNode }) {
  return (
    <div className="dashboard-panel px-4 py-3">
      <div className="text-xs uppercase tracking-wide text-zinc-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold text-zinc-900 dark:text-zinc-50">{value}</div>
      {hint && <div className="mt-0.5 text-xs text-zinc-500">{hint}</div>}
    </div>
  )
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="grid gap-1 text-sm">
      <span className="text-zinc-600 dark:text-zinc-300">{label}</span>
      {children}
      {hint && <span className="text-xs text-zinc-500">{hint}</span>}
    </label>
  )
}

export const inputClass =
  "h-9 w-full rounded-lg border border-black/10 bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-[var(--ring)] dark:border-white/10 dark:bg-white/5"
