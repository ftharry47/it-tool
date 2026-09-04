import { useMemo } from 'react'
import { useRoutes, Navigate } from 'react-router-dom'
import type { RouteObject } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { Loading } from '../components/ui/Loading'
import { AppLayout } from '../components/layout/AppLayout'
import { highestRole } from './utils'
import { Login } from '../pages/shared/Login'
import { Home } from '../pages/home/Home'
import { CatalogBrowse } from '../pages/home/CatalogBrowse'
import { KbBrowse } from '../pages/home/KbBrowse'
import { KbArticleView } from '../pages/home/KbArticleView'
import { Dashboard } from '../pages/dashboard/Dashboard'
import { ProblemList } from '../pages/dashboard/ProblemList'
import { ProblemDetail } from '../pages/dashboard/ProblemDetail'
import { ServiceRequestList } from '../pages/dashboard/ServiceRequestList'
import { ServiceRequestDetail } from '../pages/dashboard/ServiceRequestDetail'
import { KbArticleList } from '../pages/dashboard/KbArticleList'
import { KbArticleEditor } from '../pages/dashboard/KbArticleEditor'
import { ChangeList } from '../pages/dashboard/ChangeList'
import { ChangeDetail } from '../pages/dashboard/ChangeDetail'
import { ChangeCalendar } from '../pages/dashboard/ChangeCalendar'
import { IncidentDetail } from '../pages/dashboard/IncidentDetail'
import { ReportsDashboard } from '../pages/dashboard/ReportsDashboard'
import { SavedReportList } from '../pages/dashboard/SavedReportList'
import { AdHocQueryBuilder } from '../pages/dashboard/AdHocQueryBuilder'
import { NotificationPreferences } from '../pages/dashboard/NotificationPreferences'
import { SlaDetails } from '../pages/dashboard/SlaDetails'
import { Settings } from '../pages/dashboard/Settings'
import { ProjectList } from '../pages/dashboard/ProjectList'
import { ProjectDetail } from '../pages/dashboard/ProjectDetail'
import { BoardPage } from '../pages/dashboard/BoardPage'
import { IssueDetail } from '../pages/dashboard/IssueDetail'
import { Incidents } from '../pages/shared/Incidents'
import { AdminHome } from '../pages/admin/AdminHome'
import { UserAdmin } from '../pages/admin/UserAdmin'
import { CatalogAdmin } from '../pages/admin/CatalogAdmin'
import { WorkflowAdmin } from '../pages/admin/WorkflowAdmin'
import { AutomationAdmin } from '../pages/admin/AutomationAdmin'
import { BusinessCalendars } from '../pages/admin/BusinessCalendars'
import { NotFound } from '../pages/shared/NotFound'
import type { RouteDefinition } from './types'

const homeRoutes: RouteDefinition[] = [
  { path: 'home', label: 'Home', element: <Home /> },
  { path: 'home/incidents', label: 'My Incidents', element: <Incidents /> },
  { path: 'home/incidents/:id', label: 'Incident Detail', element: <IncidentDetail /> },
  { path: 'home/catalog', label: 'Service Catalog', element: <CatalogBrowse /> },
  { path: 'home/kb', label: 'Knowledge Base', element: <KbBrowse /> },
  { path: 'home/kb/:id', label: 'Article', element: <KbArticleView /> },
  { path: 'home/notifications', label: 'Notifications', element: <NotificationPreferences />, hidden: true },
]

const dashboardRoutes: RouteDefinition[] = [
  { path: 'dashboard', label: 'Dashboard', element: <Dashboard /> },
  { path: 'dashboard/incidents', label: 'Incidents', element: <Incidents /> },
  { path: 'dashboard/incidents/:id', label: 'Incident Detail', element: <IncidentDetail /> },
  { path: 'dashboard/service-requests', label: 'Service Requests', element: <ServiceRequestList /> },
  { path: 'dashboard/service-requests/new', label: 'New Request', element: <CatalogBrowse /> },
  { path: 'dashboard/service-requests/:id', label: 'Request Detail', element: <ServiceRequestDetail /> },
  { path: 'dashboard/problems', label: 'Problems', element: <ProblemList /> },
  { path: 'dashboard/problems/:id', label: 'Problem Detail', element: <ProblemDetail /> },
  { path: 'dashboard/changes', label: 'Changes', element: <ChangeList /> },
  { path: 'dashboard/changes/calendar', label: 'Change Calendar', element: <ChangeCalendar /> },
  { path: 'dashboard/changes/:id', label: 'Change Detail', element: <ChangeDetail /> },
  { path: 'dashboard/board', label: 'Board', element: <BoardPage /> },
  { path: 'dashboard/projects', label: 'Projects', element: <ProjectList /> },
  { path: 'dashboard/projects/:id', label: 'Project Detail', element: <ProjectDetail /> },
  { path: 'dashboard/projects/:projectId/issues/:issueId', label: 'Issue Detail', element: <IssueDetail /> },
  { path: 'dashboard/kb', label: 'KB Articles', element: <KbArticleList /> },
  { path: 'dashboard/kb/:id', label: 'KB Editor', element: <KbArticleEditor /> },
  { path: 'dashboard/reports', label: 'Reports', element: <ReportsDashboard /> },
  { path: 'dashboard/reports/saved', label: 'Saved Reports', element: <SavedReportList /> },
  { path: 'dashboard/reports/query', label: 'Ad-Hoc Query', element: <AdHocQueryBuilder /> },
  { path: 'dashboard/sla', label: 'SLA', element: <SlaDetails /> },
  { path: 'dashboard/notifications', label: 'Notifications', element: <NotificationPreferences /> },
  { path: 'dashboard/settings', label: 'Settings', element: <Settings />, hidden: true },
]

const adminRoutes: RouteDefinition[] = [
  { path: 'admin', label: 'Admin', element: <AdminHome /> },
  { path: 'admin/users', label: 'Users', element: <UserAdmin /> },
  { path: 'admin/catalog', label: 'Catalog', element: <CatalogAdmin /> },
  { path: 'admin/workflows', label: 'Workflows', element: <WorkflowAdmin /> },
  { path: 'admin/automation', label: 'Automation', element: <AutomationAdmin /> },
  { path: 'admin/business-calendars', label: 'Business Calendars', element: <BusinessCalendars /> },
]

const defaultRoute: Record<string, string> = {
  END_USER: '/home',
  AGENT: '/dashboard',
  TEAM_LEAD: '/dashboard',
  ADMIN: '/admin',
  SUPER_ADMIN: '/admin',
}

export function getRouteDefinitions(role: string): RouteDefinition[] {
  if (role === 'ADMIN' || role === 'SUPER_ADMIN') return [...dashboardRoutes, ...adminRoutes]
  if (role === 'AGENT' || role === 'TEAM_LEAD') return dashboardRoutes
  return homeRoutes
}

export function getRoutesForRole(role: string): RouteObject[] {
  const defs = getRouteDefinitions(role)
  const children = defs.map((def) => ({
    path: def.path,
    element: def.element,
  }))

  return [
    {
      path: '/',
      element: <AppLayout />,
      children: [
        { index: true, element: <Navigate to={defaultRoute[role] ?? '/home'} replace /> },
        ...children,
        { path: '*', element: <NotFound /> },
      ],
    },
    { path: 'login', element: <Navigate to={defaultRoute[role] ?? '/home'} replace /> },
  ]
}

export function AppRoutes() {
  const { currentUser, isAuthenticated, loading } = useAuth()

  const routeObjects = useMemo(() => {
    if (!isAuthenticated || !currentUser) return [{ path: '*', element: <Login /> }]
    const role = highestRole(currentUser.roles)
    return getRoutesForRole(role)
  }, [isAuthenticated, currentUser])

  if (loading) return <Loading message="Signing you in…" />
  return useRoutes(routeObjects)
}
