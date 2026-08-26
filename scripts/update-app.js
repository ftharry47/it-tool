const fs = require('fs');
const path = require('path');

const appContent = `import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  BarChart3,
  Bell,
  BookOpen,
  Boxes,
  Building2,
  Calendar,
  Camera,
  Check,
  CheckCircle,
  ChevronDown,
  ChevronRight,
  Clock,
  Command,
  Compass,
  Cpu,
  Download,
  ExternalLink,
  Eye,
  FileCheck,
  FileSpreadsheet,
  FileText,
  Filter,
  Flame,
  Globe,
  Grid,
  HardDrive,
  Headphones,
  HelpCircle,
  History,
  Key,
  Kanban,
  Laptop,
  Layers,
  LayoutDashboard,
  LifeBuoy,
  ListFilter,
  Loader2,
  Lock,
  LogIn,
  LogOut,
  Mail,
  Maximize2,
  Menu,
  MessageSquare,
  MinusCircle,
  Monitor,
  MoreHorizontal,
  MoreVertical,
  Network,
  Paperclip,
  Phone,
  Plus,
  PlusCircle,
  Radio,
  RefreshCw,
  Search,
  Send,
  Server,
  Settings,
  Share2,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Sliders,
  Smartphone,
  Sparkles,
  Tag,
  Terminal,
  TrendingUp,
  User,
  UserCheck,
  UserCircle,
  UserPlus,
  Users,
  Wifi,
  Wrench,
  X,
  XCircle,
  Zap,
} from 'lucide-react'

/* =============================================================================
   TYPES & ITIL DATA STRUCTURES
   ============================================================================= */

export type TicketStatus = 'Open' | 'In Progress' | 'On Hold' | 'Differ' | 'Resolved'
export type Priority = 'Pending' | 'Low' | 'Medium' | 'High' | 'Critical'
export type IssueType =
  | 'Software & Applications'
  | 'Hardware & Peripherals'
  | 'VPN / Network Connectivity'
  | 'Email & Communications'
  | 'Access & Security'
  | 'Cloud Infrastructure'
  | 'Other'

export type ImpactArea =
  | 'Individual User'
  | 'Entire Department'
  | 'Entire Facility'
  | 'Service Request'
  | 'System Outage'
  | 'Security / Access'

export interface Ticket {
  'Ticket ID': string
  Timestamp?: string
  Name: string
  'Email Address': string
  'Phone Number': string
  Location: string
  'Temporary Email'?: string
  'Short Description': string
  'Additional Description'?: string
  Status: TicketStatus
  Priority: Priority
  'Assigned To'?: string
  'Resolved By'?: string
  'Resolution Notes'?: string
  'Critical Flag'?: string
  'Issue Type'?: IssueType
  'Impact Area'?: ImpactArea
  'Image Attachment'?: string
  'Resolution Due'?: string
  'SLA Breached'?: string
  'SLA Target Hours'?: number
  'Created At'?: string
  'Updated At'?: string
  ciId?: string
}

export interface UserAccount {
  email: string
  displayName: string
  role: 'Admin' | 'L2' | 'L1' | 'Viewer' | 'User'
  supportLevel?: string
  isAvailable?: boolean
  department?: string
  avatarUrl?: string
}

export interface CMDBItem {
  id: string
  name: string
  type: 'Server' | 'Laptop' | 'Database' | 'Network' | 'Cloud VPC' | 'SaaS Application'
  status: 'Operational' | 'Degraded' | 'Maintenance' | 'Critical'
  owner: string
  ipAddress?: string
  location?: string
  lastUpdated?: string
}

export interface AuditEvent {
  id: string
  action: string
  targetType: string
  targetId: string
  performedBy: string
  timestamp: string
  details?: string
}

/* =============================================================================
   API WRAPPER
   ============================================================================= */

async function callAPI<T = any>(fn: string, ...args: any[]): Promise<T> {
  const currentUser = JSON.parse(localStorage.getItem('it_user_session') || 'null')
  const res = await fetch(\`/api/\${fn}\`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ args, user: currentUser }),
  })
  if (!res.ok) {
    const errorText = await res.text()
    try {
      const errObj = JSON.parse(errorText)
      throw new Error(errObj.error || \`API error \${res.status}\`)
    } catch {
      throw new Error(\`API error \${res.status}: \${errorText}\`)
    }
  }
  return res.json()
}

/* =============================================================================
   CONTEXT & STATE MANAGEMENT
   ============================================================================= */

interface ITContextType {
  currentUser: UserAccount | null
  tickets: Ticket[]
  cmdbItems: CMDBItem[]
  auditEvents: AuditEvent[]
  settings: Record<string, any>
  isLoading: boolean
  autoAssignEnabled: boolean
  activeView: string
  setActiveView: (view: string) => void
  refreshData: () => Promise<void>
  loginWithMicrosoft: (mockRole?: 'Admin' | 'L2' | 'L1' | 'User') => Promise<void>
  logout: () => void
  createTicket: (ticketData: any) => Promise<any>
  updateTicketStatus: (ticketId: string, newStatus: TicketStatus, notes?: string) => Promise<void>
  assignTicket: (ticketId: string, assignedTo: string, priority?: Priority) => Promise<void>
  toggleAutoAssign: (enabled: boolean) => Promise<void>
  canManage: boolean
  isTechnician: boolean
}

const ITContext = createContext<ITContextType | null>(null)

export function useITPortal() {
  const ctx = useContext(ITContext)
  if (!ctx) throw new Error('useITPortal must be used within ITProvider')
  return ctx
}

/* =============================================================================
   MAIN PROVIDER COMPONENT
   ============================================================================= */

export function ITProvider({ children }: { children: React.ReactNode }) {
  const [currentUser, setCurrentUser] = useState<UserAccount | null>(() => {
    try {
      const saved = localStorage.getItem('it_user_session')
      return saved ? JSON.parse(saved) : {
        email: 'admin@work.local',
        displayName: 'Enterprise Administrator',
        role: 'Admin',
        supportLevel: 'L3',
        department: 'Global IT Operations',
      }
    } catch {
      return null
    }
  })

  const [tickets, setTickets] = useState<Ticket[]>([])
  const [cmdbItems, setCmdbItems] = useState<CMDBItem[]>([])
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([])
  const [settings, setSettings] = useState<Record<string, any>>({})
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [autoAssignEnabled, setAutoAssignEnabled] = useState<boolean>(true)
  const [activeView, setActiveView] = useState<string>('kanban')

  const canManage = currentUser?.role === 'Admin' || currentUser?.role === 'L2'
  const isTechnician = currentUser?.role === 'Admin' || currentUser?.role === 'L2' || currentUser?.role === 'L1'

  // Fetch all core system data
  const refreshData = useCallback(async () => {
    setIsLoading(true)
    try {
      const [dashRes, cmdbRes, settingsRes] = await Promise.allSettled([
        callAPI('getDashboardData'),
        callAPI('getCMDBItems'),
        callAPI('getAllSettings'),
      ])

      if (dashRes.status === 'fulfilled' && dashRes.value) {
        if (Array.isArray(dashRes.value.tickets)) {
          setTickets(dashRes.value.tickets)
        }
      }

      if (cmdbRes.status === 'fulfilled' && Array.isArray(cmdbRes.value)) {
        setCmdbItems(cmdbRes.value)
      } else {
        setCmdbItems([
          { id: 'CI-1001', name: 'PRD-AZURE-K8S-01', type: 'Cloud VPC', status: 'Operational', owner: 'DevOps Team', ipAddress: '10.240.0.12', location: 'East US 2' },
          { id: 'CI-1002', name: 'SQL-GLOBAL-PRIMARY', type: 'Database', status: 'Operational', owner: 'DBA Ops', ipAddress: '10.240.4.88', location: 'Central US' },
          { id: 'CI-1003', name: 'AUTH-ENTRA-GATEWAY', type: 'Server', status: 'Operational', owner: 'SecOps', ipAddress: '10.240.1.15', location: 'East US 2' },
          { id: 'CI-1004', name: 'VPN-GATEWAY-CHICAGO', type: 'Network', status: 'Degraded', owner: 'Network Eng', ipAddress: '172.16.0.1', location: 'Chicago HQ' },
          { id: 'CI-1005', name: 'EXEC-MBP-SRIHARI', type: 'Laptop', status: 'Operational', owner: 'SriHari Thangavel', location: 'Executive Floor' },
        ])
      }

      if (settingsRes.status === 'fulfilled' && settingsRes.value) {
        setSettings(settingsRes.value)
        if (settingsRes.value.AUTO_ASSIGN !== undefined) {
          setAutoAssignEnabled(settingsRes.value.AUTO_ASSIGN === 'true' || settingsRes.value.AUTO_ASSIGN === true)
        }
      }
    } catch (e) {
      console.error('Failed to load enterprise data:', e)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    refreshData()
    const es = new EventSource('/api/events')
    es.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        setAuditEvents((prev) => [data, ...prev.slice(0, 49)])
        refreshData()
      } catch {}
    }
    return () => es.close()
  }, [refreshData])

  const loginWithMicrosoft = async (mockRole: 'Admin' | 'L2' | 'L1' | 'User' = 'Admin') => {
    const rolesMap: Record<string, UserAccount> = {
      Admin: {
        email: 'admin@work.local',
        displayName: 'Enterprise Lead (Azure Admin)',
        role: 'Admin',
        supportLevel: 'L3',
        department: 'Global Enterprise IT',
      },
      L2: {
        email: 'agent@work.local',
        displayName: 'Sarah Connor (Tier 2 Incident Lead)',
        role: 'L2',
        supportLevel: 'L2',
        department: 'Service Desk Ops',
      },
      L1: {
        email: 'dinesh.manoharan@alignedcardio.com',
        displayName: 'Dinesh Manoharan (IT Specialist)',
        role: 'L1',
        supportLevel: 'L1',
        department: 'Field Support',
      },
      User: {
        email: 'user@work.local',
        displayName: 'Alex Morgan (Staff Engineer)',
        role: 'User',
        department: 'Clinical Engineering',
      },
    }

    const account = rolesMap[mockRole]
    setCurrentUser(account)
    localStorage.setItem('it_user_session', JSON.stringify(account))
    await refreshData()
  }

  const logout = () => {
    setCurrentUser(null)
    localStorage.removeItem('it_user_session')
  }

  const createTicket = async (formData: any) => {
    const res = await callAPI('submitTicket', formData)
    await refreshData()
    return res
  }

  const updateTicketStatus = async (ticketId: string, newStatus: TicketStatus, notes?: string) => {
    await callAPI('updateTicketStatus', ticketId, newStatus, currentUser?.displayName || 'IT Staff', notes || '')
    setTickets((prev) =>
      prev.map((t) => (t['Ticket ID'] === ticketId ? { ...t, Status: newStatus } : t))
    )
  }

  const assignTicket = async (ticketId: string, assignedTo: string, priority?: Priority) => {
    await callAPI('assignTicket', ticketId, assignedTo, currentUser?.displayName || 'Lead', priority || null)
    setTickets((prev) =>
      prev.map((t) =>
        t['Ticket ID'] === ticketId
          ? { ...t, 'Assigned To': assignedTo, Priority: priority || t.Priority, Status: 'In Progress' }
          : t
      )
    )
  }

  const toggleAutoAssign = async (enabled: boolean) => {
    await callAPI('toggleAutoAssign', enabled, currentUser?.displayName || 'Admin')
    setAutoAssignEnabled(enabled)
  }

  return (
    <ITContext.Provider
      value={{
        currentUser,
        tickets,
        cmdbItems,
        auditEvents,
        settings,
        isLoading,
        autoAssignEnabled,
        activeView,
        setActiveView,
        refreshData,
        loginWithMicrosoft,
        logout,
        createTicket,
        updateTicketStatus,
        assignTicket,
        toggleAutoAssign,
        canManage,
        isTechnician,
      }}
    >
      {children}
    </ITContext.Provider>
  )
}

/* =============================================================================
   MICROSOFT 365 / ENTRA ID AUTH SCREEN & MODAL
   ============================================================================= */

function MicrosoftAuthModal({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  const { loginWithMicrosoft } = useITPortal()
  const [isAuthenticating, setIsAuthenticating] = useState(false)
  const [selectedRole, setSelectedRole] = useState<'Admin' | 'L2' | 'L1' | 'User'>('Admin')

  if (!isOpen) return null

  const handleSignIn = async () => {
    setIsAuthenticating(true)
    setTimeout(async () => {
      await loginWithMicrosoft(selectedRole)
      setIsAuthenticating(false)
      onClose()
    }, 800)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
      <motion.div
        initial={{ opacity: 0, scale: 0.95, y: 10 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95 }}
        className="w-full max-w-md bg-obsidian-900 border border-silver-700/60 rounded-2xl shadow-titanium overflow-hidden"
      >
        <div className="p-8 border-b border-silver-800 bg-gradient-to-b from-obsidian-850 to-obsidian-900 text-center">
          <div className="inline-flex items-center justify-center p-3 bg-obsidian-800 rounded-xl border border-silver-700 shadow-inner mb-4">
            <div className="grid grid-cols-2 gap-1 w-6 h-6">
              <div className="bg-[#F25022] rounded-xs" />
              <div className="bg-[#7FBA00] rounded-xs" />
              <div className="bg-[#00A4EF] rounded-xs" />
              <div className="bg-[#FFB900] rounded-xs" />
            </div>
          </div>
          <h2 className="text-xl font-bold tracking-tight text-white font-sans">
            Microsoft 365 Enterprise SSO
          </h2>
          <p className="text-sm text-silver-400 mt-1">
            Azure Entra ID Directory &bull; Aligned IT Global
          </p>
        </div>

        <div className="p-6 space-y-5">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-silver-400 uppercase tracking-wider">
              Select Corporate Identity Profile
            </label>
            <div className="grid grid-cols-2 gap-2">
              {[
                { id: 'Admin', title: 'Global Admin', badge: 'L3 / Full Control' },
                { id: 'L2', title: 'IT Ops Lead', badge: 'Tier 2 Incident' },
                { id: 'L1', title: 'IT Support Engineer', badge: 'Tier 1 Triage' },
                { id: 'User', title: 'Employee Portal', badge: 'Self Service' },
              ].map((role) => (
                <button
                  key={role.id}
                  onClick={() => setSelectedRole(role.id as any)}
                  className={\`p-3 rounded-xl text-left border transition-all \${
                    selectedRole === role.id
                      ? 'bg-silver-800/80 border-electric-blue text-white shadow-glow-blue'
                      : 'bg-obsidian-850 border-silver-800/80 text-silver-300 hover:border-silver-600'
                  }\`}
                >
                  <div className="text-sm font-semibold">{role.title}</div>
                  <div className="text-[11px] text-silver-400 font-mono mt-0.5">{role.badge}</div>
                </button>
              ))}
            </div>
          </div>

          <div className="p-3 bg-obsidian-950 rounded-xl border border-silver-800/80 text-xs text-silver-400 flex items-center gap-2.5">
            <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>FIDO2 / Conditional Access &amp; Intune Device Compliant</span>
          </div>

          <button
            onClick={handleSignIn}
            disabled={isAuthenticating}
            className="w-full flex items-center justify-center gap-3 py-3.5 px-4 bg-white hover:bg-silver-100 text-obsidian-950 font-bold rounded-xl transition-all shadow-lg hover:shadow-glow-silver disabled:opacity-50"
          >
            {isAuthenticating ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <>
                <div className="grid grid-cols-2 gap-0.5 w-4 h-4">
                  <div className="bg-[#F25022]" />
                  <div className="bg-[#7FBA00]" />
                  <div className="bg-[#00A4EF]" />
                  <div className="bg-[#FFB900]" />
                </div>
                <span>Authenticate with Microsoft</span>
              </>
            )}
          </button>
        </div>

        <div className="px-6 py-3 bg-obsidian-950 border-t border-silver-800/80 flex items-center justify-between text-xs text-silver-500">
          <span>Tenant: alignedcardio.onmicrosoft.com</span>
          <button onClick={onClose} className="hover:text-silver-300 transition-colors">
            Dismiss
          </button>
        </div>
      </motion.div>
    </div>
  )
}

/* =============================================================================
   TOP COMMAND BAR & NAVIGATION
   ============================================================================= */

function EnterpriseHeader({ onOpenAuth, onOpenNewTicket }: { onOpenAuth: () => void; onOpenNewTicket: () => void }) {
  const { currentUser, logout, autoAssignEnabled, toggleAutoAssign, canManage } = useITPortal()
  const [showUserMenu, setShowUserMenu] = useState(false)

  return (
    <header className="sticky top-0 z-40 h-16 border-b border-silver-800/80 bg-obsidian-950/80 backdrop-blur-xl px-6 flex items-center justify-between">
      <div className="flex items-center gap-6">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-silver-600 via-silver-400 to-white flex items-center justify-center shadow-glow-silver">
            <Zap className="w-5 h-5 text-obsidian-950 stroke-[2.5]" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-extrabold tracking-tight text-white font-sans text-base">
                CORE<span className="text-silver-400">OPS</span>
              </span>
              <span className="text-[10px] uppercase tracking-widest font-mono px-1.5 py-0.5 rounded bg-silver-800 border border-silver-700 text-silver-300">
                ITIL v4
              </span>
            </div>
            <div className="text-[11px] text-silver-400 flex items-center gap-1.5 font-medium">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              <span>Global Enterprise Service Desk</span>
            </div>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <button
          onClick={onOpenNewTicket}
          className="flex items-center gap-2 px-3.5 py-2 bg-gradient-to-r from-silver-200 to-white hover:from-white hover:to-silver-300 text-obsidian-950 font-bold text-xs rounded-lg transition-all shadow-glow-silver"
        >
          <Plus className="w-4 h-4 stroke-[2.5]" />
          <span>New Incident / Request</span>
        </button>

        {canManage && (
          <button
            onClick={() => toggleAutoAssign(!autoAssignEnabled)}
            className={\`flex items-center gap-2 px-3 py-1.5 rounded-lg border text-xs font-semibold transition-all \${
              autoAssignEnabled
                ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-300'
                : 'bg-obsidian-850 border-silver-800 text-silver-400'
            }\`}
            title="Ticket Auto-Assignment Engine"
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Auto-Assign:</span>
            <span className="font-mono uppercase text-[10px]">{autoAssignEnabled ? 'ON' : 'OFF'}</span>
          </button>
        )}

        <div className="relative">
          {currentUser ? (
            <button
              onClick={() => setShowUserMenu(!showUserMenu)}
              className="flex items-center gap-2.5 p-1.5 pr-3 bg-obsidian-850 hover:bg-obsidian-800 border border-silver-800 rounded-xl transition-all"
            >
              <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-silver-700 to-silver-900 border border-silver-700 flex items-center justify-center text-xs font-bold text-white">
                {currentUser.displayName.charAt(0)}
              </div>
              <div className="text-left hidden md:block">
                <div className="text-xs font-semibold text-white leading-tight">
                  {currentUser.displayName.split(' ')[0]}
                </div>
                <div className="text-[10px] text-silver-400 font-mono leading-tight">
                  {currentUser.role}
                </div>
              </div>
              <ChevronDown className="w-3.5 h-3.5 text-silver-400" />
            </button>
          ) : (
            <button
              onClick={onOpenAuth}
              className="flex items-center gap-2 px-3 py-2 bg-obsidian-800 hover:bg-obsidian-750 border border-silver-700 rounded-xl text-xs font-bold text-white transition-all shadow-sm"
            >
              <div className="grid grid-cols-2 gap-0.5 w-3.5 h-3.5">
                <div className="bg-[#F25022]" />
                <div className="bg-[#7FBA00]" />
                <div className="bg-[#00A4EF]" />
                <div className="bg-[#FFB900]" />
              </div>
              <span>Sign in with Microsoft</span>
            </button>
          )}

          {showUserMenu && currentUser && (
            <div className="absolute right-0 mt-2 w-64 bg-obsidian-900 border border-silver-700 rounded-xl shadow-titanium p-2 z-50">
              <div className="p-3 border-b border-silver-800 mb-1">
                <div className="text-xs font-bold text-white">{currentUser.displayName}</div>
                <div className="text-[11px] text-silver-400 truncate">{currentUser.email}</div>
                <div className="mt-1.5 inline-block text-[10px] font-mono px-2 py-0.5 bg-silver-800 text-silver-200 rounded border border-silver-700">
                  {currentUser.department || 'Enterprise Staff'} &bull; {currentUser.role}
                </div>
              </div>
              <button
                onClick={() => {
                  setShowUserMenu(false)
                  onOpenAuth()
                }}
                className="w-full text-left px-3 py-2 text-xs text-silver-300 hover:text-white hover:bg-obsidian-800 rounded-lg flex items-center gap-2"
              >
                <Users className="w-3.5 h-3.5" />
                <span>Switch Microsoft Account</span>
              </button>
              <button
                onClick={() => {
                  setShowUserMenu(false)
                  logout()
                }}
                className="w-full text-left px-3 py-2 text-xs text-rose-400 hover:text-rose-300 hover:bg-rose-950/30 rounded-lg flex items-center gap-2"
              >
                <LogOut className="w-3.5 h-3.5" />
                <span>Sign Out</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}

/* =============================================================================
   SIDEBAR NAVIGATION (ServiceNow & Jira Hybrid)
   ============================================================================= */

function EnterpriseSidebar() {
  const { activeView, setActiveView, isTechnician, canManage } = useITPortal()

  const navItems = [
    { id: 'kanban', label: 'Jira Agile Board', icon: Kanban, badge: 'Active' },
    { id: 'catalog', label: 'Service Catalog', icon: Layers, badge: 'ITIL' },
    { id: 'cmdb', label: 'CMDB & Assets', icon: HardDrive },
    { id: 'analytics', label: 'Operations Analytics', icon: BarChart3 },
    { id: 'knowledge', label: 'Knowledge & AI Bot', icon: BookOpen },
    { id: 'telemetry', label: 'Live Telemetry', icon: Radio },
    ...(canManage ? [{ id: 'settings', label: 'System Admin', icon: Settings }] : []),
  ]

  return (
    <aside className="w-64 border-r border-silver-800/80 bg-obsidian-950 flex flex-col justify-between p-4 shrink-0 hidden lg:flex min-h-[calc(100vh-4rem)]">
      <div className="space-y-6">
        <div className="px-3">
          <div className="text-[11px] font-bold uppercase tracking-wider text-silver-500 font-mono">
            Platform Workspaces
          </div>
        </div>

        <nav className="space-y-1">
          {navItems.map((item) => {
            const Icon = item.icon
            const isActive = activeView === item.id
            return (
              <button
                key={item.id}
                onClick={() => setActiveView(item.id)}
                className={\`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition-all \${
                  isActive
                    ? 'bg-silver-800 text-white border border-silver-700 shadow-glow-silver'
                    : 'text-silver-400 hover:text-silver-200 hover:bg-obsidian-900 border border-transparent'
                }\`}
              >
                <div className="flex items-center gap-3">
                  <Icon className={\`w-4 h-4 \${isActive ? 'text-white' : 'text-silver-400'}\`} />
                  <span>{item.label}</span>
                </div>
                {item.badge && (
                  <span
                    className={\`text-[10px] font-mono px-1.5 py-0.5 rounded \${
                      isActive ? 'bg-white text-obsidian-950 font-bold' : 'bg-obsidian-850 text-silver-400 border border-silver-800'
                    }\`}
                  >
                    {item.badge}
                  </span>
                )}
              </button>
            )
          })}
        </nav>
      </div>

      <div className="p-3.5 rounded-xl bg-obsidian-900 border border-silver-800 text-xs space-y-2">
        <div className="flex items-center justify-between text-silver-400">
          <span className="font-mono text-[10px]">AZURE CLOUD HEALTH</span>
          <span className="inline-flex items-center gap-1 text-[10px] text-emerald-400 font-bold">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping" />
            99.98%
          </span>
        </div>
        <div className="text-[11px] text-silver-300 font-medium truncate">
          Node.js API &bull; Microsoft 365 Connected
        </div>
      </div>
    </aside>
  )
}

/* =============================================================================
   JIRA-GRADE INTERACTIVE KANBAN BOARD
   ============================================================================= */

function JiraKanbanBoard({ onOpenTicketModal }: { onOpenTicketModal: (ticket: Ticket) => void }) {
  const { tickets, updateTicketStatus, currentUser, isTechnician } = useITPortal()
  const [filterPriority, setFilterPriority] = useState<string>('ALL')
  const [filterSearch, setFilterSearch] = useState<string>('')
  const [filterAssignee, setFilterAssignee] = useState<string>('ALL')

  const columns: { id: TicketStatus; title: string; color: string; badgeClass: string }[] = [
    { id: 'Open', title: 'Backlog / New Triage', color: 'border-silver-600', badgeClass: 'bg-silver-800 text-silver-300' },
    { id: 'In Progress', title: 'In Investigation / Progress', color: 'border-blue-500', badgeClass: 'bg-blue-950/60 text-blue-300 border-blue-800' },
    { id: 'On Hold', title: 'Pending Approval / Vendor', color: 'border-amber-500', badgeClass: 'bg-amber-950/60 text-amber-300 border-amber-800' },
    { id: 'Differ', title: 'Deferred / Backlog Review', color: 'border-purple-500', badgeClass: 'bg-purple-950/60 text-purple-300 border-purple-800' },
    { id: 'Resolved', title: 'Resolved & Closed', color: 'border-emerald-500', badgeClass: 'bg-emerald-950/60 text-emerald-300 border-emerald-800' },
  ]

  const filteredTickets = useMemo(() => {
    return tickets.filter((t) => {
      const matchSearch =
        !filterSearch ||
        t['Ticket ID'].toLowerCase().includes(filterSearch.toLowerCase()) ||
        t['Short Description'].toLowerCase().includes(filterSearch.toLowerCase()) ||
        t.Name.toLowerCase().includes(filterSearch.toLowerCase())
      const matchPriority = filterPriority === 'ALL' || t.Priority === filterPriority
      const matchAssignee =
        filterAssignee === 'ALL' ||
        (filterAssignee === 'UNASSIGNED' && !t['Assigned To']) ||
        (filterAssignee === 'MINE' && t['Assigned To'] === currentUser?.displayName)
      return matchSearch && matchPriority && matchAssignee
    })
  }, [tickets, filterSearch, filterPriority, filterAssignee, currentUser])

  return (
    <div className="space-y-6">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 p-4 rounded-2xl bg-obsidian-900 border border-silver-800">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <Kanban className="w-5 h-5 text-silver-300" />
            <span>Incident &amp; Request Kanban Matrix</span>
          </h1>
          <p className="text-xs text-silver-400 mt-0.5">
            Real-time Jira workflow transitions &bull; Interactive SLA timers &bull; Auto-balanced queues
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-silver-500 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search ID, user, issue..."
              value={filterSearch}
              onChange={(e) => setFilterSearch(e.target.value)}
              className="pl-8 pr-3 py-1.5 bg-obsidian-950 border border-silver-800 rounded-lg text-xs text-white placeholder-silver-500 focus:outline-none focus:border-silver-500 w-48"
            />
          </div>

          <select
            value={filterPriority}
            onChange={(e) => setFilterPriority(e.target.value)}
            className="px-2.5 py-1.5 bg-obsidian-950 border border-silver-800 rounded-lg text-xs text-silver-300 focus:outline-none focus:border-silver-500"
          >
            <option value="ALL">All Priorities</option>
            <option value="Critical">P1 - Critical</option>
            <option value="High">P2 - High</option>
            <option value="Medium">P3 - Medium</option>
            <option value="Low">P4 - Low</option>
          </select>

          <select
            value={filterAssignee}
            onChange={(e) => setFilterAssignee(e.target.value)}
            className="px-2.5 py-1.5 bg-obsidian-950 border border-silver-800 rounded-lg text-xs text-silver-300 focus:outline-none focus:border-silver-500"
          >
            <option value="ALL">All Assignees</option>
            <option value="MINE">Assigned to Me</option>
            <option value="UNASSIGNED">Unassigned Queue</option>
          </select>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 xl:grid-cols-5 gap-4 items-start">
        {columns.map((col) => {
          const colTickets = filteredTickets.filter((t) => t.Status === col.id)
          return (
            <div
              key={col.id}
              className="bg-obsidian-900/90 border border-silver-800/80 rounded-2xl p-3.5 flex flex-col min-h-[520px] shadow-sm"
            >
              <div className="flex items-center justify-between pb-3 border-b border-silver-800/80 mb-3">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-silver-200">{col.title}</span>
                </div>
                <span className={\`text-[11px] font-mono px-2 py-0.5 rounded-full border \${col.badgeClass}\`}>
                  {colTickets.length}
                </span>
              </div>

              <div className="space-y-3 flex-1 overflow-y-auto max-h-[680px] pr-1">
                {colTickets.length === 0 ? (
                  <div className="h-32 flex flex-col items-center justify-center text-silver-600 border border-dashed border-silver-800/80 rounded-xl text-xs">
                    <span>No tickets in lane</span>
                  </div>
                ) : (
                  colTickets.map((ticket) => (
                    <motion.div
                      key={ticket['Ticket ID']}
                      layoutId={ticket['Ticket ID']}
                      onClick={() => onOpenTicketModal(ticket)}
                      className="p-3.5 rounded-xl bg-obsidian-850 hover:bg-obsidian-800 border border-silver-800/80 hover:border-silver-600 cursor-pointer transition-all shadow-sm group space-y-2.5"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-mono text-xs font-bold text-silver-300 group-hover:text-white">
                          {ticket['Ticket ID']}
                        </span>
                        <span
                          className={\`text-[10px] font-bold px-2 py-0.5 rounded border uppercase tracking-wider \${
                            ticket.Priority === 'Critical'
                              ? 'bg-rose-950/80 text-rose-300 border-rose-700 shadow-glow-rose'
                              : ticket.Priority === 'High'
                              ? 'bg-amber-950/80 text-amber-300 border-amber-700'
                              : ticket.Priority === 'Medium'
                              ? 'bg-blue-950/80 text-blue-300 border-blue-700'
                              : 'bg-silver-900 text-silver-400 border-silver-700'
                          }\`}
                        >
                          {ticket.Priority || 'P3'}
                        </span>
                      </div>

                      <p className="text-xs font-medium text-silver-200 line-clamp-2 leading-relaxed">
                        {ticket['Short Description']}
                      </p>

                      <div className="flex items-center justify-between text-[11px] text-silver-400 pt-1 border-t border-silver-800/60">
                        <div className="flex items-center gap-1.5 truncate">
                          <UserCircle className="w-3.5 h-3.5 text-silver-500 shrink-0" />
                          <span className="truncate">{ticket.Name}</span>
                        </div>
                        {ticket['Issue Type'] && (
                          <span className="text-[10px] font-mono px-1.5 py-0.5 bg-obsidian-950 border border-silver-800 rounded text-silver-400 shrink-0">
                            {ticket['Issue Type'].split(' ')[0]}
                          </span>
                        )}
                      </div>

                      <div className="flex items-center justify-between pt-1">
                        <div className="flex items-center gap-1.5 text-[11px]">
                          <div className="w-5 h-5 rounded-full bg-silver-800 border border-silver-700 flex items-center justify-center text-[10px] font-bold text-silver-300">
                            {ticket['Assigned To'] ? ticket['Assigned To'].charAt(0) : '?'}
                          </div>
                          <span className="text-silver-400 truncate max-w-[90px]">
                            {ticket['Assigned To'] || 'Unassigned'}
                          </span>
                        </div>

                        {isTechnician && (
                          <div
                            className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity"
                            onClick={(e) => e.stopPropagation()}
                          >
                            {col.id !== 'In Progress' && (
                              <button
                                onClick={() => updateTicketStatus(ticket['Ticket ID'], 'In Progress', 'Moved via Kanban')}
                                className="p-1 hover:bg-blue-900/50 text-blue-400 rounded text-[10px]"
                                title="Move to In Progress"
                              >
                                ⚡
                              </button>
                            )}
                            {col.id !== 'Resolved' && (
                              <button
                                onClick={() => updateTicketStatus(ticket['Ticket ID'], 'Resolved', 'Resolved via Kanban')}
                                className="p-1 hover:bg-emerald-900/50 text-emerald-400 rounded text-[10px]"
                                title="Resolve Ticket"
                              >
                                ✓
                              </button>
                            )}
                          </div>
                        )}
                      </div>
                    </motion.div>
                  ))
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

/* =============================================================================
   SERVICENOW ITIL SERVICE CATALOG
   ============================================================================= */

function ServiceNowCatalog({ onOpenRequest }: { onOpenRequest: (category: string) => void }) {
  const catalogItems = [
    {
      id: 'hardware',
      title: 'Hardware & Workstation Provisioning',
      desc: 'Request enterprise laptops, high-res monitors, docking stations, and ergonomic peripherals.',
      icon: Laptop,
      badge: 'IT Delivery: 24h',
    },
    {
      id: 'software',
      title: 'Enterprise Software & SaaS Licenses',
      desc: 'Microsoft 365 E5, Adobe Creative Cloud, JetBrains, Figma Enterprise, Docker Pro.',
      icon: Cpu,
      badge: 'Auto-Approved',
    },
    {
      id: 'cloud',
      title: 'Cloud & Kubernetes IAM Access',
      desc: 'Azure subscription roles, AWS IAM assume roles, Kubernetes namespace elevated access.',
      icon: Server,
      badge: 'SecOps Review',
    },
    {
      id: 'vpn',
      title: 'Global VPN & Zero-Trust Remote Access',
      desc: 'Cisco AnyConnect tokens, Cloudflare Zero-Trust certificates, and firewall port forwarding.',
      icon: Network,
      badge: 'Automated',
    },
    {
      id: 'security',
      title: 'Security Incident & Phishing Report',
      desc: 'Urgent response for suspicious emails, compromised credentials, or endpoint anomalies.',
      icon: ShieldAlert,
      badge: 'P1 Severity',
    },
    {
      id: 'onboarding',
      title: 'Employee Onboarding & Offboarding',
      desc: 'End-to-end IT setup for new enterprise hires, accounts, equipment, and access packages.',
      icon: UserPlus,
      badge: 'HR / IT Workflow',
    },
  ]

  return (
    <div className="space-y-6">
      <div className="p-8 rounded-3xl bg-gradient-to-r from-obsidian-900 via-obsidian-850 to-obsidian-900 border border-silver-800 text-center relative overflow-hidden">
        <div className="max-w-2xl mx-auto space-y-3 relative z-10">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-silver-800/60 border border-silver-700 text-silver-300 text-xs font-mono">
            <Boxes className="w-3.5 h-3.5 text-silver-300" />
            <span>ITIL Service Request Catalog</span>
          </div>
          <h1 className="text-3xl font-extrabold tracking-tight text-white font-sans">
            How can Global IT assist you today?
          </h1>
          <p className="text-sm text-silver-400">
            Submit standardized service requests, order pre-approved hardware packages, and request automated cloud credentials.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {catalogItems.map((item) => {
          const Icon = item.icon
          return (
            <motion.div
              key={item.id}
              whileHover={{ y: -3 }}
              onClick={() => onOpenRequest(item.title)}
              className="p-6 rounded-2xl bg-obsidian-900 border border-silver-800 hover:border-silver-600 transition-all cursor-pointer shadow-sm hover:shadow-glow-silver flex flex-col justify-between group"
            >
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <div className="w-12 h-12 rounded-xl bg-obsidian-800 border border-silver-700 flex items-center justify-center text-white group-hover:bg-white group-hover:text-obsidian-950 transition-colors shadow-inner">
                    <Icon className="w-6 h-6 stroke-[2]" />
                  </div>
                  <span className="text-[10px] font-mono px-2 py-0.5 bg-obsidian-950 border border-silver-800 rounded text-silver-300">
                    {item.badge}
                  </span>
                </div>
                <div>
                  <h3 className="text-base font-bold text-white group-hover:text-silver-200 transition-colors">
                    {item.title}
                  </h3>
                  <p className="text-xs text-silver-400 mt-1.5 leading-relaxed">{item.desc}</p>
                </div>
              </div>

              <div className="pt-4 mt-4 border-t border-silver-800/60 flex items-center justify-between text-xs font-semibold text-silver-300 group-hover:text-white">
                <span>Request Service</span>
                <ChevronRight className="w-4 h-4 text-silver-500 group-hover:text-white group-hover:translate-x-1 transition-all" />
              </div>
            </motion.div>
          )
        })}
      </div>
    </div>
  )
}

/* =============================================================================
   SERVICENOW CMDB & ASSET INVENTORY
   ============================================================================= */

function ServiceNowCMDB() {
  const { cmdbItems } = useITPortal()
  const [search, setSearch] = useState('')

  const filtered = cmdbItems.filter(
    (c) =>
      c.name.toLowerCase().includes(search.toLowerCase()) ||
      c.type.toLowerCase().includes(search.toLowerCase()) ||
      c.owner.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 p-5 rounded-2xl bg-obsidian-900 border border-silver-800">
        <div>
          <h1 className="text-xl font-bold text-white flex items-center gap-2.5">
            <HardDrive className="w-5 h-5 text-silver-300" />
            <span>Configuration Management Database (CMDB)</span>
          </h1>
          <p className="text-xs text-silver-400 mt-0.5">
            Enterprise assets, Cloud VPCs, Database clusters, and CI relationship tracking
          </p>
        </div>
        <div className="relative">
          <Search className="w-3.5 h-3.5 text-silver-500 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Search CI name, type, owner..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-8 pr-3 py-1.5 bg-obsidian-950 border border-silver-800 rounded-lg text-xs text-white placeholder-silver-500 focus:outline-none focus:border-silver-500 w-56"
          />
        </div>
      </div>

      <div className="bg-obsidian-900 border border-silver-800 rounded-2xl overflow-hidden shadow-sm">
        <table className="w-full text-left text-xs">
          <thead className="bg-obsidian-950 border-b border-silver-800 text-silver-400 font-mono text-[11px] uppercase">
            <tr>
              <th className="px-5 py-3.5">CI Identifier</th>
              <th className="px-5 py-3.5">Configuration Item</th>
              <th className="px-5 py-3.5">Classification</th>
              <th className="px-5 py-3.5">Operational Status</th>
              <th className="px-5 py-3.5">Service Owner</th>
              <th className="px-5 py-3.5">IP / Endpoint</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-silver-800/60">
            {filtered.map((item) => (
              <tr key={item.id} className="hover:bg-obsidian-850/80 transition-colors">
                <td className="px-5 py-4 font-mono font-bold text-silver-300">{item.id}</td>
                <td className="px-5 py-4 font-semibold text-white">{item.name}</td>
                <td className="px-5 py-4 text-silver-400">{item.type}</td>
                <td className="px-5 py-4">
                  <span
                    className={\`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-bold border \${
                      item.status === 'Operational'
                        ? 'bg-emerald-950/60 text-emerald-300 border-emerald-800'
                        : item.status === 'Degraded'
                        ? 'bg-amber-950/60 text-amber-300 border-amber-800'
                        : 'bg-rose-950/60 text-rose-300 border-rose-800'
                    }\`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current" />
                    {item.status}
                  </span>
                </td>
                <td className="px-5 py-4 text-silver-300">{item.owner}</td>
                <td className="px-5 py-4 font-mono text-silver-400">{item.ipAddress || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

/* =============================================================================
   NEW INCIDENT / SERVICE REQUEST MODAL
   ============================================================================= */

function CreateTicketModal({
  isOpen,
  onClose,
  initialTitle,
}: {
  isOpen: boolean
  onClose: () => void
  initialTitle?: string
}) {
  const { createTicket, currentUser } = useITPortal()
  const [formData, setFormData] = useState({
    name: currentUser?.displayName || '',
    email: currentUser?.email || '',
    phone: '555-0199',
    location: 'Corporate HQ - Floor 3',
    issueType: 'Software & Applications' as IssueType,
    impactArea: 'Individual User' as ImpactArea,
    shortDescription: initialTitle || '',
    additionalDescription: '',
    criticalFlag: false,
    imageAttachment: '',
  })
  const [isSubmitting, setIsSubmitting] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (initialTitle) {
      setFormData((prev) => ({ ...prev, shortDescription: initialTitle }))
    }
  }, [initialTitle])

  if (!isOpen) return null

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      setFormData((prev) => ({ ...prev, imageAttachment: reader.result as string }))
    }
    reader.readAsDataURL(file)
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsSubmitting(true)
    try {
      await createTicket({
        name: formData.name,
        email: formData.email,
        phone: formData.phone,
        location: formData.location,
        issueType: formData.issueType,
        impactArea: formData.impactArea,
        shortDescription: formData.shortDescription,
        additionalDescription: formData.additionalDescription,
        criticalFlag: formData.criticalFlag ? 'true' : 'false',
        imageAttachment: formData.imageAttachment,
      })
      onClose()
    } catch (err: any) {
      alert(\`Submission error: \${err.message}\`)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4 overflow-y-auto">
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.95 }}
        className="w-full max-w-2xl bg-obsidian-900 border border-silver-700/80 rounded-2xl shadow-titanium overflow-hidden my-8"
      >
        <div className="p-6 border-b border-silver-800 bg-obsidian-850 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-silver-800 border border-silver-700 flex items-center justify-center text-white">
              <Flame className="w-5 h-5 text-rose-400" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white">Raise Enterprise Incident / Request</h2>
              <p className="text-xs text-silver-400">ITIL Incident &amp; Request Management Workflow</p>
            </div>
          </div>
          <button onClick={onClose} className="text-silver-500 hover:text-white transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-semibold text-silver-400 block mb-1">Requester Full Name</label>
              <input
                type="text"
                required
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="w-full px-3 py-2 bg-obsidian-950 border border-silver-800 rounded-lg text-xs text-white focus:outline-none focus:border-silver-500"
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-silver-400 block mb-1">Corporate Email Address</label>
              <input
                type="email"
                required
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                className="w-full px-3 py-2 bg-obsidian-950 border border-silver-800 rounded-lg text-xs text-white focus:outline-none focus:border-silver-500"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-semibold text-silver-400 block mb-1">Issue Classification</label>
              <select
                value={formData.issueType}
                onChange={(e) => setFormData({ ...formData, issueType: e.target.value as any })}
                className="w-full px-3 py-2 bg-obsidian-950 border border-silver-800 rounded-lg text-xs text-white focus:outline-none focus:border-silver-500"
              >
                <option value="Software & Applications">Software &amp; Applications</option>
                <option value="Hardware & Peripherals">Hardware &amp; Peripherals</option>
                <option value="VPN / Network Connectivity">VPN / Network Connectivity</option>
                <option value="Email & Communications">Email &amp; Communications</option>
                <option value="Access & Security">Access &amp; Security</option>
                <option value="Cloud Infrastructure">Cloud Infrastructure</option>
              </select>
            </div>
            <div>
              <label className="text-xs font-semibold text-silver-400 block mb-1">Impact Level</label>
              <select
                value={formData.impactArea}
                onChange={(e) => setFormData({ ...formData, impactArea: e.target.value as any })}
                className="w-full px-3 py-2 bg-obsidian-950 border border-silver-800 rounded-lg text-xs text-white focus:outline-none focus:border-silver-500"
              >
                <option value="Individual User">Individual User (Standard)</option>
                <option value="Entire Department">Entire Department</option>
                <option value="Entire Facility">Entire Facility / Outage</option>
                <option value="Service Request">Standard Service Request</option>
              </select>
            </div>
          </div>

          <div>
            <label className="text-xs font-semibold text-silver-400 block mb-1">Short Subject / Summary</label>
            <input
              type="text"
              required
              placeholder="e.g. Cannot access Azure Kubernetes cluster or VPN token expired"
              value={formData.shortDescription}
              onChange={(e) => setFormData({ ...formData, shortDescription: e.target.value })}
              className="w-full px-3 py-2 bg-obsidian-950 border border-silver-800 rounded-lg text-xs text-white focus:outline-none focus:border-silver-500"
            />
          </div>

          <div>
            <label className="text-xs font-semibold text-silver-400 block mb-1">Detailed Description &amp; Symptoms</label>
            <textarea
              rows={3}
              placeholder="Provide exact error codes, steps to reproduce, or CI asset details..."
              value={formData.additionalDescription}
              onChange={(e) => setFormData({ ...formData, additionalDescription: e.target.value })}
              className="w-full px-3 py-2 bg-obsidian-950 border border-silver-800 rounded-lg text-xs text-white focus:outline-none focus:border-silver-500 resize-none"
            />
          </div>

          <div className="p-4 bg-obsidian-950 border border-silver-800 rounded-xl space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-silver-300 flex items-center gap-2">
                <Paperclip className="w-3.5 h-3.5 text-silver-400" />
                <span>Screenshot / Diagnostic Evidence</span>
              </span>
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="px-2.5 py-1 bg-obsidian-800 hover:bg-silver-800 text-silver-200 border border-silver-700 rounded text-[11px] font-semibold transition-colors"
              >
                Choose File
              </button>
            </div>
            <input ref={fileInputRef} type="file" accept="image/*" onChange={handleImageUpload} className="hidden" />
            {formData.imageAttachment && (
              <div className="mt-2 flex items-center gap-3">
                <img src={formData.imageAttachment} alt="Diagnostic" className="w-16 h-12 object-cover rounded border border-silver-700" />
                <span className="text-xs text-emerald-400 font-semibold">Image Attached &bull; Ready to upload</span>
              </div>
            )}
          </div>

          <div className="flex items-center gap-3 p-3 bg-rose-950/30 border border-rose-900/60 rounded-xl">
            <input
              type="checkbox"
              id="criticalCheck"
              checked={formData.criticalFlag}
              onChange={(e) => setFormData({ ...formData, criticalFlag: e.target.checked })}
              className="w-4 h-4 rounded text-rose-600 bg-obsidian-950 border-silver-700 focus:ring-0"
            />
            <label htmlFor="criticalCheck" className="text-xs font-bold text-rose-300 cursor-pointer">
              Flag as Mission-Critical Outage (Escalates directly to Tier 2 Lead)
            </label>
          </div>

          <div className="pt-4 border-t border-silver-800 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold text-silver-400 hover:text-white transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-5 py-2.5 bg-white hover:bg-silver-200 text-obsidian-950 font-bold text-xs rounded-xl transition-all shadow-glow-silver disabled:opacity-50"
            >
              {isSubmitting ? 'Dispatching...' : 'Dispatch Ticket'}
            </button>
          </div>
        </form>
      </motion.div>
    </div>
  )
}

/* =============================================================================
   DETAILED TICKET VIEW MODAL
   ============================================================================= */

function TicketDetailModal({ ticket, onClose }: { ticket: Ticket | null; onClose: () => void }) {
  const { updateTicketStatus, isTechnician } = useITPortal()
  const [resolutionNotes, setResolutionNotes] = useState('')

  if (!ticket) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4 overflow-y-auto">
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.95 }}
        className="w-full max-w-3xl bg-obsidian-900 border border-silver-700/80 rounded-2xl shadow-titanium overflow-hidden my-8"
      >
        <div className="p-6 border-b border-silver-800 bg-obsidian-850 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="font-mono text-sm font-bold text-silver-300">{ticket['Ticket ID']}</span>
            <span className="text-xs font-bold px-2 py-0.5 rounded bg-silver-800 text-silver-200 border border-silver-700">
              {ticket.Status}
            </span>
          </div>
          <button onClick={onClose} className="text-silver-500 hover:text-white transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-6 max-h-[75vh] overflow-y-auto">
          <div>
            <h2 className="text-lg font-bold text-white">{ticket['Short Description']}</h2>
            {ticket['Additional Description'] && (
              <p className="text-xs text-silver-300 mt-2 p-3 bg-obsidian-950 border border-silver-800 rounded-xl leading-relaxed whitespace-pre-wrap">
                {ticket['Additional Description']}
              </p>
            )}
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div className="p-3 bg-obsidian-950 border border-silver-800/80 rounded-xl">
              <span className="text-[10px] text-silver-500 uppercase font-mono">Requester</span>
              <div className="text-xs font-semibold text-white mt-0.5 truncate">{ticket.Name}</div>
              <div className="text-[11px] text-silver-400 truncate">{ticket['Email Address']}</div>
            </div>
            <div className="p-3 bg-obsidian-950 border border-silver-800/80 rounded-xl">
              <span className="text-[10px] text-silver-500 uppercase font-mono">Priority</span>
              <div className="text-xs font-bold text-amber-300 mt-0.5">{ticket.Priority || 'Medium'}</div>
            </div>
            <div className="p-3 bg-obsidian-950 border border-silver-800/80 rounded-xl">
              <span className="text-[10px] text-silver-500 uppercase font-mono">Assigned Staff</span>
              <div className="text-xs font-semibold text-white mt-0.5">{ticket['Assigned To'] || 'Unassigned'}</div>
            </div>
            <div className="p-3 bg-obsidian-950 border border-silver-800/80 rounded-xl">
              <span className="text-[10px] text-silver-500 uppercase font-mono">Classification</span>
              <div className="text-xs font-semibold text-silver-300 mt-0.5 truncate">{ticket['Issue Type'] || 'General'}</div>
            </div>
          </div>

          {ticket['Image Attachment'] && (
            <div>
              <span className="text-xs font-semibold text-silver-400 block mb-2">Attached Diagnostic Screenshot</span>
              <a href={ticket['Image Attachment']} target="_blank" rel="noreferrer">
                <img
                  src={ticket['Image Attachment']}
                  alt="Attachment"
                  className="max-h-64 rounded-xl border border-silver-700 hover:border-silver-400 transition-colors cursor-zoom-in"
                />
              </a>
            </div>
          )}

          {isTechnician && (
            <div className="p-4 bg-obsidian-950 border border-silver-800 rounded-xl space-y-3">
              <span className="text-xs font-bold text-silver-300 uppercase tracking-wider font-mono">
                ITIL Resolution Controls
              </span>
              <div className="flex flex-wrap gap-2">
                <button
                  onClick={() => updateTicketStatus(ticket['Ticket ID'], 'In Progress', 'Accepted by tech')}
                  className="px-3 py-1.5 bg-blue-950/80 hover:bg-blue-900 border border-blue-700 text-blue-300 rounded-lg text-xs font-semibold"
                >
                  Mark In Progress
                </button>
                <button
                  onClick={() => updateTicketStatus(ticket['Ticket ID'], 'On Hold', 'Waiting on user')}
                  className="px-3 py-1.5 bg-amber-950/80 hover:bg-amber-900 border border-amber-700 text-amber-300 rounded-lg text-xs font-semibold"
                >
                  Place On Hold
                </button>
                <button
                  onClick={() => {
                    updateTicketStatus(ticket['Ticket ID'], 'Resolved', resolutionNotes || 'Resolved by technician')
                    onClose()
                  }}
                  className="px-3 py-1.5 bg-emerald-950/80 hover:bg-emerald-900 border border-emerald-700 text-emerald-300 rounded-lg text-xs font-semibold"
                >
                  Resolve &amp; Close Ticket
                </button>
              </div>
            </div>
          )}
        </div>
      </motion.div>
    </div>
  )
}

/* =============================================================================
   APP MASTER CONTAINER
   ============================================================================= */

export default function App() {
  const [isAuthOpen, setIsAuthOpen] = useState(false)
  const [isNewTicketOpen, setIsNewTicketOpen] = useState(false)
  const [selectedCatalogItem, setSelectedCatalogItem] = useState<string>('')
  const [activeTicketModal, setActiveTicketModal] = useState<Ticket | null>(null)

  return (
    <ITProvider>
      <div className="min-h-screen bg-obsidian-950 text-silver-100 flex flex-col font-sans">
        <EnterpriseHeader
          onOpenAuth={() => setIsAuthOpen(true)}
          onOpenNewTicket={() => {
            setSelectedCatalogItem('')
            setIsNewTicketOpen(true)
          }}
        />

        <div className="flex flex-1">
          <EnterpriseSidebar />

          <main className="flex-1 p-6 lg:p-8 max-w-7xl mx-auto w-full">
            <AppViewRenderer
              onOpenTicketModal={(t) => setActiveTicketModal(t)}
              onOpenCatalogRequest={(category) => {
                setSelectedCatalogItem(category)
                setIsNewTicketOpen(true)
              }}
            />
          </main>
        </div>

        <MicrosoftAuthModal isOpen={isAuthOpen} onClose={() => setIsAuthOpen(false)} />
        <CreateTicketModal
          isOpen={isNewTicketOpen}
          initialTitle={selectedCatalogItem}
          onClose={() => setIsNewTicketOpen(false)}
        />
        <TicketDetailModal ticket={activeTicketModal} onClose={() => setActiveTicketModal(null)} />
      </div>
    </ITProvider>
  )
}

/* =============================================================================
   VIEW ROUTER & DISPATCHER
   ============================================================================= */

function AppViewRenderer({
  onOpenTicketModal,
  onOpenCatalogRequest,
}: {
  onOpenTicketModal: (ticket: Ticket) => void
  onOpenCatalogRequest: (cat: string) => void
}) {
  const { activeView } = useITPortal()

  switch (activeView) {
    case 'kanban':
      return <JiraKanbanBoard onOpenTicketModal={onOpenTicketModal} />
    case 'catalog':
      return <ServiceNowCatalog onOpenRequest={onOpenCatalogRequest} />
    case 'cmdb':
      return <ServiceNowCMDB />
    case 'incidents':
      return <JiraKanbanBoard onOpenTicketModal={onOpenTicketModal} />
    case 'analytics':
      return <OperationsAnalytics />
    case 'knowledge':
      return <KnowledgeAndBot />
    case 'telemetry':
      return <LiveTelemetryFeed />
    case 'settings':
      return <SystemSettingsView />
    default:
      return <JiraKanbanBoard onOpenTicketModal={onOpenTicketModal} />
  }
}

/* =============================================================================
   OPERATIONS ANALYTICS VIEW
   ============================================================================= */

function OperationsAnalytics() {
  const { tickets } = useITPortal()
  const total = tickets.length
  const open = tickets.filter((t) => t.Status === 'Open').length
  const inProgress = tickets.filter((t) => t.Status === 'In Progress').length
  const resolved = tickets.filter((t) => t.Status === 'Resolved').length
  const critical = tickets.filter((t) => t.Priority === 'Critical').length

  return (
    <div className="space-y-6">
      <div className="p-5 rounded-2xl bg-obsidian-900 border border-silver-800">
        <h1 className="text-xl font-bold text-white flex items-center gap-2">
          <BarChart3 className="w-5 h-5 text-silver-300" />
          <span>ITIL Executive Analytics &amp; Service SLA</span>
        </h1>
        <p className="text-xs text-silver-400 mt-0.5">
          Mean Time To Resolution (MTTR), SLA compliance rate, and workload balance
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {[
          { label: 'Total Managed Tickets', val: total, color: 'text-white' },
          { label: 'Active Investigation Queue', val: open + inProgress, color: 'text-blue-400' },
          { label: 'Mission-Critical Outages', val: critical, color: 'text-rose-400' },
          { label: 'Resolved Tickets (30d)', val: resolved, color: 'text-emerald-400' },
        ].map((stat, i) => (
          <div key={i} className="p-5 rounded-2xl bg-obsidian-900 border border-silver-800 space-y-1">
            <span className="text-xs text-silver-400 font-medium">{stat.label}</span>
            <div className={\`text-2xl font-extrabold \${stat.color}\`}>{stat.val}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

/* =============================================================================
   KNOWLEDGE BASE & IT HELP-BOT
   ============================================================================= */

function KnowledgeAndBot() {
  const [messages, setMessages] = useState<{ sender: 'bot' | 'user'; text: string }[]>([
    {
      sender: 'bot',
      text: 'Hello! I am your AI IT Operations Assistant. I can help reset passwords, resolve VPN errors, request software licenses, or guide you through hardware provisioning.',
    },
  ])
  const [input, setInput] = useState('')

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault()
    if (!input.trim()) return
    const userMsg = input.trim()
    setMessages((prev) => [...prev, { sender: 'user', text: userMsg }])
    setInput('')

    setTimeout(() => {
      let botReply = 'I have logged your query and checked our ITIL Knowledge base.'
      if (userMsg.toLowerCase().includes('vpn')) {
        botReply = 'For VPN connectivity issues: Please ensure Cisco AnyConnect is on v4.10, authenticate with Microsoft Entra MFA, and verify your home gateway is not blocking UDP port 443.'
      } else if (userMsg.toLowerCase().includes('password') || userMsg.toLowerCase().includes('reset')) {
        botReply = 'Self-service password reset is enabled via Microsoft Entra SSPR at https://passwordreset.microsoftonline.com.'
      } else if (userMsg.toLowerCase().includes('laptop') || userMsg.toLowerCase().includes('hardware')) {
        botReply = 'Hardware requests can be submitted instantly through our Service Catalog tab with manager sign-off.'
      }
      setMessages((prev) => [...prev, { sender: 'bot', text: botReply }])
    }, 600)
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
      <div className="bg-obsidian-900 border border-silver-800 rounded-2xl p-5 flex flex-col h-[520px]">
        <div className="flex items-center gap-3 pb-3 border-b border-silver-800 mb-3">
          <Sparkles className="w-5 h-5 text-silver-300" />
          <div>
            <h2 className="text-sm font-bold text-white">IT AI Virtual Agent</h2>
            <p className="text-[11px] text-silver-400">Trained on Enterprise Knowledge Articles</p>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto space-y-3 pr-2">
          {messages.map((m, i) => (
            <div
              key={i}
              className={\`flex \${m.sender === 'user' ? 'justify-end' : 'justify-start'}\`}
            >
              <div
                className={\`max-w-[85%] p-3 rounded-2xl text-xs leading-relaxed \${
                  m.sender === 'user'
                    ? 'bg-silver-800 text-white border border-silver-700'
                    : 'bg-obsidian-950 text-silver-200 border border-silver-800'
                }\`}
              >
                {m.text}
              </div>
            </div>
          ))}
        </div>

        <form onSubmit={handleSend} className="pt-3 border-t border-silver-800 flex gap-2">
          <input
            type="text"
            placeholder="Ask about VPN, password reset, software..."
            value={input}
            onChange={(e) => setInput(e.target.value)}
            className="flex-1 px-3 py-2 bg-obsidian-950 border border-silver-800 rounded-xl text-xs text-white focus:outline-none focus:border-silver-500"
          />
          <button
            type="submit"
            className="px-4 py-2 bg-white hover:bg-silver-200 text-obsidian-950 font-bold text-xs rounded-xl"
          >
            Send
          </button>
        </form>
      </div>

      <div className="space-y-4">
        <div className="p-5 rounded-2xl bg-obsidian-900 border border-silver-800">
          <h2 className="text-base font-bold text-white mb-1">Recommended Knowledge Articles</h2>
          <p className="text-xs text-silver-400">Standard operating procedures and troubleshooting guides</p>
        </div>

        {[
          { title: 'KB00142: Microsoft Authenticator Number Matching Setup', time: '3 min read' },
          { title: 'KB00210: Connecting to Global Enterprise VPN from Remote Workstations', time: '5 min read' },
          { title: 'KB00388: Docker Pro & WSL2 Configuration on Windows 11', time: '8 min read' },
        ].map((kb, i) => (
          <div
            key={i}
            className="p-4 rounded-xl bg-obsidian-900 border border-silver-800 hover:border-silver-600 transition-colors cursor-pointer flex items-center justify-between"
          >
            <div className="flex items-center gap-3">
              <BookOpen className="w-4 h-4 text-silver-400" />
              <span className="text-xs font-semibold text-silver-200">{kb.title}</span>
            </div>
            <span className="text-[10px] font-mono text-silver-500">{kb.time}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

/* =============================================================================
   LIVE TELEMETRY & AUDIT FEED
   ============================================================================= */

function LiveTelemetryFeed() {
  const { auditEvents } = useITPortal()

  return (
    <div className="space-y-6">
      <div className="p-5 rounded-2xl bg-obsidian-900 border border-silver-800">
        <h1 className="text-xl font-bold text-white flex items-center gap-2">
          <Radio className="w-5 h-5 text-emerald-400 animate-pulse" />
          <span>Real-Time Audit &amp; Telemetry Stream</span>
        </h1>
        <p className="text-xs text-silver-400 mt-0.5">
          Live Server-Sent Events (SSE) telemetry covering ticket mutations and security events
        </p>
      </div>

      <div className="bg-obsidian-900 border border-silver-800 rounded-2xl p-4 space-y-3">
        {auditEvents.length === 0 ? (
          <div className="text-center py-12 text-xs text-silver-500">
            Awaiting real-time telemetry events from Express backend...
          </div>
        ) : (
          auditEvents.map((ev, i) => (
            <div
              key={i}
              className="p-3 bg-obsidian-950 border border-silver-800 rounded-xl flex items-center justify-between text-xs font-mono"
            >
              <div className="flex items-center gap-3">
                <span className="px-2 py-0.5 bg-silver-800 text-silver-200 rounded font-bold text-[10px]">
                  {ev.action}
                </span>
                <span className="text-white font-semibold">{ev.targetId}</span>
                <span className="text-silver-400">{ev.details || 'Event triggered'}</span>
              </div>
              <span className="text-silver-500 text-[11px]">{new Date(ev.timestamp).toLocaleTimeString()}</span>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

/* =============================================================================
   SYSTEM SETTINGS VIEW
   ============================================================================= */

function SystemSettingsView() {
  const { autoAssignEnabled, toggleAutoAssign } = useITPortal()

  return (
    <div className="space-y-6">
      <div className="p-5 rounded-2xl bg-obsidian-900 border border-silver-800">
        <h1 className="text-xl font-bold text-white flex items-center gap-2">
          <Settings className="w-5 h-5 text-silver-300" />
          <span>Platform Administration &amp; Automation Rules</span>
        </h1>
        <p className="text-xs text-silver-400 mt-0.5">
          Manage auto-assignment algorithms, Microsoft Entra SSO policies, and SLA thresholds
        </p>
      </div>

      <div className="p-6 bg-obsidian-900 border border-silver-800 rounded-2xl space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-sm font-bold text-white">Ticket Auto-Assignment Engine</h3>
            <p className="text-xs text-silver-400 mt-0.5">
              Automatically routes incoming incidents to qualified IT staff based on current workload and tier.
            </p>
          </div>
          <button
            onClick={() => toggleAutoAssign(!autoAssignEnabled)}
            className={\`px-4 py-2 rounded-xl text-xs font-bold transition-all \${
              autoAssignEnabled
                ? 'bg-emerald-500 text-obsidian-950 shadow-glow-emerald'
                : 'bg-obsidian-850 border border-silver-700 text-silver-400'
            }\`}
          >
            {autoAssignEnabled ? 'ENABLED' : 'DISABLED'}
          </button>
        </div>
      </div>
    </div>
  )
}
`;

fs.writeFileSync(path.join(__dirname, '..', 'client', 'src', 'App.tsx'), appContent, 'utf8');
console.log('App.tsx written successfully');
