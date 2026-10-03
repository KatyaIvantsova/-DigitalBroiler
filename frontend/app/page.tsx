"use client"

/* eslint-disable react-hooks/set-state-in-effect */

import { useEffect, useState } from "react"
import { DashboardHeader, type DashboardSection } from "@/components/dashboard/header"
import { NotificationsPage } from "@/components/dashboard/notifications-page"
import { IncidentsPage } from "@/components/dashboard/incidents-page"
import { IncidentAnalyticsPage } from "@/components/dashboard/incident-analytics-page"
import { TasksPage } from "@/components/dashboard/tasks-page"
import { NotificationBanner } from "@/components/dashboard/notification-banner"
import { FlockOverview } from "@/components/overview/flock-overview"
import type { IncidentRegistryFilters } from "@/lib/incident-analytics"

const SECTIONS: DashboardSection[] = ["technical", "incidents", "analytics", "notifications", "tasks"]

export default function DashboardPage() {
  const [activeSection, setActiveSection] = useState<DashboardSection>("technical")
  const [selectedIncidentId, setSelectedIncidentId] = useState<string>()

  useEffect(() => {
    const section = new URLSearchParams(window.location.search).get("section")
    if (section && (SECTIONS as string[]).includes(section)) {
      setActiveSection(section as DashboardSection)
    }
  }, [])

  const handleOpenIncidentCard = (incidentId: string) => {
    setSelectedIncidentId(incidentId)
    setActiveSection("incidents")
  }

  const handleSectionChange = (section: DashboardSection) => {
    setActiveSection(section)
    const params = new URLSearchParams()
    params.set("section", section)
    window.history.replaceState(null, "", `${window.location.pathname}?${params.toString()}`)
  }

  const handleOpenIncidentRegistry = (filters: IncidentRegistryFilters) => {
    const params = new URLSearchParams({
      section: "incidents",
      periodDays: String(filters.periodDays),
    })

    if (filters.workshop) params.set("workshop", filters.workshop)
    if (filters.house) params.set("house", filters.house)
    if (filters.type) params.set("type", filters.type)
    if (filters.priority) params.set("priority", filters.priority)
    if (filters.status) params.set("status", filters.status)

    window.history.pushState(null, "", `${window.location.pathname}?${params.toString()}`)
    setSelectedIncidentId(undefined)
    setActiveSection("incidents")
  }

  return (
    <div className="min-h-screen px-3 py-3 md:px-5 md:py-5">
      <div className="dashboard-shell mx-auto max-w-[1680px] rounded-[28px]">
        <DashboardHeader
          activeSection={activeSection}
          onSectionChange={handleSectionChange}
        />

        {activeSection === "notifications" ? (
          <NotificationsPage />
        ) : activeSection === "incidents" ? (
          <IncidentsPage selectedIncidentId={selectedIncidentId} />
        ) : activeSection === "analytics" ? (
          <IncidentAnalyticsPage onOpenRegistry={handleOpenIncidentRegistry} />
        ) : activeSection === "tasks" ? (
          <TasksPage />
        ) : (
          <div className="space-y-4 p-3 md:p-4">
            <NotificationBanner onOpen={handleOpenIncidentCard} />
            <FlockOverview onOpenIncident={handleOpenIncidentCard} />
          </div>
        )}
      </div>
    </div>
  )
}
