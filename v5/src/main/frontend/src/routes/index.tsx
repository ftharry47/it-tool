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
import { MyRequests } from '../pages/home/MyRequests'
import { MyRequestDetail } from '../pages/home/MyRequestDetail'
import { ApprovedRequests } from '../pages/shared/ApprovedRequests'
import { ApprovedRequestDetail } from '../pages/shared/ApprovedRequestDetail'
import { KbBrowse } from '../pages/home/KbBrowse'
import { KbArticleView } from '../pages/home/KbArticleView'
import { Dashboard } from '../pages/dashboard/Dashboard'
import { ProblemList } from '../pages/dashboard/ProblemList'
import { ProblemDetail } from '../pages/dashboard/ProblemDetail'
import { ServiceRequestList } from '../pages/dashboard/ServiceRequestList'
import { Approvals } from '../pages/dashboard/Approvals'
import { ServiceRequestDetail } from '../pages/dashboard/ServiceRequestDetail'
import { KbArticleList } from '../pages/dashboard/KbArticleList'
import { KbArticleEditor } from '../pages/dashboard/KbArticleEditor'
import { ChangeList } from '../pages/dashboard/ChangeList'
import { ChangeDetail } from '../pages/dashboard/ChangeDetail'
import { ChangeCalendar } from '../pages/dashboard/ChangeCalendar'
import { IncidentDetail } from '../pages/dashboard/IncidentDetail'
import { ReportsDashboard } from '../pages/dashboard/ReportsDashboard'
import { StandardReports } from '../pages/dashboard/StandardReports'
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
import { LocationAdmin } from '../pages/admin/LocationAdmin'
import { CategoryAdmin } from '../pages/admin/CategoryAdmin'
import { SupportTiers } from '../pages/admin/SupportTiers'
import { HowItWorks } from '../pages/admin/HowItWorks'
import { ImportTickets } from '../pages/admin/ImportTickets'
import { NotFound } from '../pages/shared/NotFound'
import type { RouteDefinition } from './types'
import type { CurrentUser } from '../auth/AuthProvider'

const homeRoutes: RouteDefinition[] = [
  { path: 'home', label: 'Home', element: <Home /> },
  { path: 'home/incidents', label: 'My Incidents', element: <Incidents /> },
  { path: 'home/incidents/:id', label: 'Incident Detail', element: <IncidentDetail /> },
  { path: 'home/catalog', label: 'Service Catalog', element: <CatalogBrowse /> },
  { path: 'home/service-requests', label: 'My Requests', element: <MyRequests /> },
  { path: 'home/service-requests/:id', label: 'Request Detail', element: <MyRequestDetail /> },
  { path: 'home/kb', label: 'Knowledge Base', element: <KbBrowse /> },
  { path: 'home/kb/:id', label: 'Article', element: <KbArticleView /> },
  { path: 'home/notifications', label: 'Notifications', element: <NotificationPreferences />, hidden: true },
]

const dashboardRoutes: RouteDefinition[] = [
  { path: 'dashboard', label: 'Dashboard', element: <Dashboard />, group: 'Overview' },
  { path: 'dashboard/incidents', label: 'Incidents', element: <Incidents />, group: 'Work' },
  { path: 'dashboard/incidents/:id', label: 'Incident Detail', element: <IncidentDetail /> },
  { path: 'dashboard/service-requests', label: 'Service Requests', element: <ServiceRequestList />, group: 'Work' },
  { path: 'dashboard/approvals', label: 'Approvals', element: <Approvals />, group: 'Work' },
  { path: 'dashboard/approved-requests', label: 'Approved Requests', element: <ApprovedRequests />, group: 'Work' },
  { path: 'dashboard/approved-requests/:id', label: 'Request Detail', element: <ApprovedRequestDetail />, hidden: true },
  { path: 'dashboard/service-requests/new', label: 'New Request', element: <CatalogBrowse /> },
  { path: 'dashboard/service-requests/:id', label: 'Request Detail', element: <ServiceRequestDetail /> },
  { path: 'dashboard/problems', label: 'Problems', element: <ProblemList />, group: 'Work' },
  { path: 'dashboard/problems/:id', label: 'Problem Detail', element: <ProblemDetail /> },
  { path: 'dashboard/changes', label: 'Changes', element: <ChangeList />, group: 'Work' },
  { path: 'dashboard/changes/calendar', label: 'Change Calendar', element: <ChangeCalendar />, group: 'Work' },
  { path: 'dashboard/changes/:id', label: 'Change Detail', element: <ChangeDetail /> },
  { path: 'dashboard/board', label: 'Board', element: <BoardPage />, group: 'Delivery' },
  { path: 'dashboard/projects', label: 'Projects', element: <ProjectList />, group: 'Delivery' },
  { path: 'dashboard/projects/:id', label: 'Project Detail', element: <ProjectDetail /> },
  { path: 'dashboard/projects/:projectId/issues/:issueId', label: 'Issue Detail', element: <IssueDetail /> },
  { path: 'dashboard/kb', label: 'KB Articles', element: <KbArticleList />, group: 'Knowledge' },
  { path: 'dashboard/kb/:id', label: 'KB Editor', element: <KbArticleEditor /> },
  { path: 'dashboard/reports', label: 'Reports', element: <ReportsDashboard />, group: 'Insights' },
  { path: 'dashboard/reports/standard', label: 'Standard Reports', element: <StandardReports />, group: 'Insights' },
  { path: 'dashboard/reports/saved', label: 'Saved Reports', element: <SavedReportList />, group: 'Insights' },
  { path: 'dashboard/reports/query', label: 'Ad-Hoc Query', element: <AdHocQueryBuilder />, group: 'Insights' },
  { path: 'dashboard/sla', label: 'SLA', element: <SlaDetails />, group: 'Insights' },
  { path: 'dashboard/notifications', label: 'Notifications', element: <NotificationPreferences />, group: 'Overview' },
  { path: 'dashboard/settings', label: 'Settings', element: <Settings />, hidden: true },
]

const adminRoutes: RouteDefinition[] = [
  { path: 'admin', label: 'Admin', element: <AdminHome />, group: 'Administration' },
  { path: 'admin/users', label: 'Users', element: <UserAdmin />, group: 'Administration' },
  { path: 'admin/support-tiers', label: 'Support Tiers', element: <SupportTiers />, group: 'Administration' },
  { path: 'admin/catalog', label: 'Catalog', element: <CatalogAdmin />, group: 'Administration' },
  { path: 'admin/workflows', label: 'Workflows', element: <WorkflowAdmin />, group: 'Administration' },
  { path: 'admin/automation', label: 'Automation', element: <AutomationAdmin />, group: 'Administration' },
  { path: 'admin/business-calendars', label: 'Business Calendars', element: <BusinessCalendars />, group: 'Administration' },
  { path: 'admin/locations', label: 'Locations', element: <LocationAdmin />, group: 'Administration' },
{ path: 'admin/categories', label: 'Categories', element: <CategoryAdmin />, group: 'Administration' },
  { path: 'admin/import-tickets', label: 'Import Tickets', element: <ImportTickets />, group: 'Administration' },
  { path: 'admin/how-it-works', label: 'How It Works', element: <HowItWorks />, group: 'Administration' },
]

const defaultRoute: Record<string, string> = {
  END_USER: '/home',
  AGENT: '/dashboard',
  TEAM_LEAD: '/dashboard',
  ADMIN: '/admin',
  SUPER_ADMIN: '/admin',
}

export function getRouteDefinitions(role: string, currentUser?: CurrentUser | null): RouteDefinition[] {
  if (role === 'ADMIN' || role === 'SUPER_ADMIN') return [...dashboardRoutes, ...adminRoutes]
  if (role === 'AGENT' || role === 'TEAM_LEAD') return dashboardRoutes
  const routes = [...homeRoutes]
  // Lightweight additive permission: location approval managers get an
  // Approvals queue in their /home context without a role change.
  if (currentUser?.isApprovalManager) {
    routes.push({ path: 'home/approvals', label: 'Approvals', element: <Approvals /> })
    routes.push({ path: 'home/approvals/:id', label: 'Request Detail', element: <ServiceRequestDetail />, hidden: true })
    routes.push({ path: 'home/approved-requests', label: 'Approved Requests', element: <ApprovedRequests /> })
    routes.push({ path: 'home/approved-requests/:id', label: 'Request Detail', element: <ApprovedRequestDetail />, hidden: true })
  }
  return routes
}

export function getRoutesForRole(role: string, currentUser?: CurrentUser | null): RouteObject[] {
  const defs = getRouteDefinitions(role, currentUser)
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
    return getRoutesForRole(role, currentUser)
  }, [isAuthenticated, currentUser])

  if (loading) return <Loading message="Signing you in…" />
  return useRoutes(routeObjects)
}
