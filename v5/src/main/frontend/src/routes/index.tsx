import { lazy, Suspense, useMemo } from 'react'
import { useRoutes, Navigate } from 'react-router-dom'
import type { RouteObject } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { Loading } from '../components/ui/Loading'
import { AppLayout } from '../components/layout/AppLayout'
import { highestRole } from './utils'
import type { RouteDefinition } from './types'
import type { CurrentUser } from '../auth/AuthProvider'

// Route-level code splitting — each page loads on demand.
const Login = lazy(() => import('../pages/shared/Login').then((m) => ({ default: m.Login })))
const Home = lazy(() => import('../pages/home/Home').then((m) => ({ default: m.Home })))
const CatalogBrowse = lazy(() => import('../pages/home/CatalogBrowse').then((m) => ({ default: m.CatalogBrowse })))
const MyRequests = lazy(() => import('../pages/home/MyRequests').then((m) => ({ default: m.MyRequests })))
const MyRequestDetail = lazy(() => import('../pages/home/MyRequestDetail').then((m) => ({ default: m.MyRequestDetail })))
const ApprovedRequests = lazy(() => import('../pages/shared/ApprovedRequests').then((m) => ({ default: m.ApprovedRequests })))
const ApprovedRequestDetail = lazy(() => import('../pages/shared/ApprovedRequestDetail').then((m) => ({ default: m.ApprovedRequestDetail })))
const KbBrowse = lazy(() => import('../pages/home/KbBrowse').then((m) => ({ default: m.KbBrowse })))
const KbArticleView = lazy(() => import('../pages/home/KbArticleView').then((m) => ({ default: m.KbArticleView })))
const Dashboard = lazy(() => import('../pages/dashboard/Dashboard').then((m) => ({ default: m.Dashboard })))
const AgentQueue = lazy(() => import('../pages/dashboard/AgentQueue').then((m) => ({ default: m.AgentQueue })))
const ProblemList = lazy(() => import('../pages/dashboard/ProblemList').then((m) => ({ default: m.ProblemList })))
const ProblemDetail = lazy(() => import('../pages/dashboard/ProblemDetail').then((m) => ({ default: m.ProblemDetail })))
const ServiceRequestList = lazy(() => import('../pages/dashboard/ServiceRequestList').then((m) => ({ default: m.ServiceRequestList })))
const Approvals = lazy(() => import('../pages/dashboard/Approvals').then((m) => ({ default: m.Approvals })))
const ServiceRequestDetail = lazy(() => import('../pages/dashboard/ServiceRequestDetail').then((m) => ({ default: m.ServiceRequestDetail })))
const KbArticleList = lazy(() => import('../pages/dashboard/KbArticleList').then((m) => ({ default: m.KbArticleList })))
const KbArticleEditor = lazy(() => import('../pages/dashboard/KbArticleEditor').then((m) => ({ default: m.KbArticleEditor })))
const ChangeList = lazy(() => import('../pages/dashboard/ChangeList').then((m) => ({ default: m.ChangeList })))
const ChangeDetail = lazy(() => import('../pages/dashboard/ChangeDetail').then((m) => ({ default: m.ChangeDetail })))
const ChangeCalendar = lazy(() => import('../pages/dashboard/ChangeCalendar').then((m) => ({ default: m.ChangeCalendar })))
const IncidentDetail = lazy(() => import('../pages/dashboard/IncidentDetail').then((m) => ({ default: m.IncidentDetail })))
const ReportsDashboard = lazy(() => import('../pages/dashboard/ReportsDashboard').then((m) => ({ default: m.ReportsDashboard })))
const AdHocQueryBuilder = lazy(() => import('../pages/dashboard/AdHocQueryBuilder').then((m) => ({ default: m.AdHocQueryBuilder })))
const DataExport = lazy(() => import('../pages/dashboard/DataExport').then((m) => ({ default: m.DataExport })))
const NotificationPreferences = lazy(() => import('../pages/dashboard/NotificationPreferences').then((m) => ({ default: m.NotificationPreferences })))
const SlaDetails = lazy(() => import('../pages/dashboard/SlaDetails').then((m) => ({ default: m.SlaDetails })))
const Settings = lazy(() => import('../pages/dashboard/Settings').then((m) => ({ default: m.Settings })))
const ProjectList = lazy(() => import('../pages/dashboard/ProjectList').then((m) => ({ default: m.ProjectList })))
const ProjectDetail = lazy(() => import('../pages/dashboard/ProjectDetail').then((m) => ({ default: m.ProjectDetail })))
const BoardPage = lazy(() => import('../pages/dashboard/BoardPage').then((m) => ({ default: m.BoardPage })))
const IssueDetail = lazy(() => import('../pages/dashboard/IssueDetail').then((m) => ({ default: m.IssueDetail })))
const Incidents = lazy(() => import('../pages/shared/Incidents').then((m) => ({ default: m.Incidents })))
const AdminHome = lazy(() => import('../pages/admin/AdminHome').then((m) => ({ default: m.AdminHome })))
const UserAdmin = lazy(() => import('../pages/admin/UserAdmin').then((m) => ({ default: m.UserAdmin })))
const CatalogAdmin = lazy(() => import('../pages/admin/CatalogAdmin').then((m) => ({ default: m.CatalogAdmin })))
const WorkflowAdmin = lazy(() => import('../pages/admin/WorkflowAdmin').then((m) => ({ default: m.WorkflowAdmin })))
const AutomationAdmin = lazy(() => import('../pages/admin/AutomationAdmin').then((m) => ({ default: m.AutomationAdmin })))
const BusinessCalendars = lazy(() => import('../pages/admin/BusinessCalendars').then((m) => ({ default: m.BusinessCalendars })))
const LocationAdmin = lazy(() => import('../pages/admin/LocationAdmin').then((m) => ({ default: m.LocationAdmin })))
const CategoryAdmin = lazy(() => import('../pages/admin/CategoryAdmin').then((m) => ({ default: m.CategoryAdmin })))
const SupportTiers = lazy(() => import('../pages/admin/SupportTiers').then((m) => ({ default: m.SupportTiers })))
const HowItWorks = lazy(() => import('../pages/admin/HowItWorks').then((m) => ({ default: m.HowItWorks })))
const ImportTickets = lazy(() => import('../pages/admin/ImportTickets').then((m) => ({ default: m.ImportTickets })))
const NotFound = lazy(() => import('../pages/shared/NotFound').then((m) => ({ default: m.NotFound })))

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
  { path: 'dashboard/agent-queue', label: 'Agent Queue', element: <AgentQueue />, hidden: true },
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
  // Standard Reports merged into Reports tabs; Saved Reports merged into Query Builder.
  { path: 'dashboard/reports/standard', label: 'Standard Reports', element: <Navigate to="/dashboard/reports" replace />, hidden: true },
  { path: 'dashboard/reports/saved', label: 'Saved Reports', element: <Navigate to="/dashboard/reports/query" replace />, hidden: true },
  { path: 'dashboard/reports/query', label: 'Query Builder', element: <AdHocQueryBuilder />, group: 'Insights' },
  { path: 'dashboard/reports/export', label: 'Data Export', element: <DataExport />, hidden: true },
  { path: 'dashboard/sla', label: 'SLA', element: <SlaDetails />, group: 'Insights' },
  { path: 'dashboard/notifications', label: 'Notifications', element: <NotificationPreferences />, group: 'Overview' },
  { path: 'dashboard/settings', label: 'Settings', element: <Settings />, hidden: true },
]

const adminRoutes: RouteDefinition[] = [
  { path: 'admin', label: 'Admin', element: <AdminHome />, group: 'Administration' },
  { path: 'admin/import-tickets', label: 'Import Tickets', element: <ImportTickets />, group: 'Administration' },
  { path: 'admin/how-it-works', label: 'How It Works', element: <HowItWorks />, group: 'Administration' },
]

// System configuration — SUPER_ADMIN only. Absent from ADMIN route defs, so
// direct URLs fall through to NotFound and sidebar nav never lists them.
const superAdminRoutes: RouteDefinition[] = [
  { path: 'admin/users', label: 'Users', element: <UserAdmin />, group: 'Administration' },
  { path: 'admin/support-tiers', label: 'Support Tiers', element: <SupportTiers />, group: 'Administration' },
  { path: 'admin/catalog', label: 'Catalog', element: <CatalogAdmin />, group: 'Administration' },
  { path: 'admin/workflows', label: 'Workflows', element: <WorkflowAdmin />, group: 'Administration' },
  { path: 'admin/automation', label: 'Automation', element: <AutomationAdmin />, group: 'Administration' },
  { path: 'admin/business-calendars', label: 'Business Calendars', element: <BusinessCalendars />, group: 'Administration' },
  { path: 'admin/locations', label: 'Locations', element: <LocationAdmin />, group: 'Administration' },
  { path: 'admin/categories', label: 'Categories', element: <CategoryAdmin />, group: 'Administration' },
]

const defaultRoute: Record<string, string> = {
  END_USER: '/home',
  AGENT: '/dashboard',
  TEAM_LEAD: '/dashboard',
  ADMIN: '/admin',
  SUPER_ADMIN: '/admin',
}

export function getRouteDefinitions(role: string, currentUser?: CurrentUser | null): RouteDefinition[] {
  if (role === 'SUPER_ADMIN') return [...dashboardRoutes, ...adminRoutes, ...superAdminRoutes]
  if (role === 'ADMIN') return [...dashboardRoutes, ...adminRoutes]
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
  return <Suspense fallback={<Loading />}>{useRoutes(routeObjects)}</Suspense>
}
