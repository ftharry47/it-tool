import { Link } from 'react-router-dom'
import { ArrowLeft, Bell, Building, ChevronRight, LogOut, Moon, Palette, Shield, User } from 'lucide-react'
import { useAuth } from '../../auth/AuthProvider'
import { useTheme } from '../../components/theme/ThemeProvider'
import { useSmartBack } from '../../lib/useSmartBack'

function SettingsCard({ title, children, icon: Icon }: { title: string; children: React.ReactNode; icon: React.ElementType }) {
  return (
    <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
      <div className="mb-4 flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-md bg-primary/10 text-primary">
          <Icon className="h-5 w-5" />
        </div>
        <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
      </div>
      {children}
    </div>
  )
}

function SettingRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col justify-between gap-1 border-b border-border py-3 last:border-0 last:pb-0 sm:flex-row sm:items-center">
      <span className="text-sm font-medium text-muted-foreground">{label}</span>
      <span className="text-sm font-medium">{children}</span>
    </div>
  )
}

function SettingsLink({ to, icon: Icon, label, description }: { to: string; icon: React.ElementType; label: string; description: string }) {
  return (
    <Link
      to={to}
      className="flex items-center justify-between rounded-md border border-border bg-background p-4 transition hover:bg-muted"
    >
      <div className="flex items-center gap-3">
        <Icon className="h-5 w-5 text-muted-foreground" />
        <div>
          <p className="font-medium">{label}</p>
          <p className="text-xs text-muted-foreground">{description}</p>
        </div>
      </div>
      <ChevronRight className="h-4 w-4 text-muted-foreground" />
    </Link>
  )
}

export function Settings() {
  const smartBack = useSmartBack('/dashboard')
  const { currentUser, logout } = useAuth()
  const { theme, setTheme } = useTheme()

  if (!currentUser) {
    return (
      <div className="flex h-full items-center justify-center p-6">
        <p className="text-muted-foreground">Loading profile…</p>
      </div>
    )
  }

  const isAdmin = currentUser.roles.includes('ADMIN') || currentUser.roles.includes('SUPER_ADMIN')

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-3xl space-y-6">
        <div>
          <div className="flex items-center gap-3">
            <button
                        onClick={smartBack}
                        className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        <ArrowLeft className="h-4 w-4" />
                        Back
                      </button>
            <div>
              <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
              <p className="text-sm text-muted-foreground">Manage your account, appearance, and organization preferences.</p>
            </div>
          </div>
        </div>

        <SettingsCard title="My Profile" icon={User}>
          <SettingRow label="Name">{currentUser.displayName}</SettingRow>
          <SettingRow label="Email">{currentUser.email}</SettingRow>
          <SettingRow label="Job title">{currentUser.jobTitle || '-'}</SettingRow>
          <SettingRow label="Department">{currentUser.department || '-'}</SettingRow>
          <SettingRow label="Roles">{currentUser.roles.join(', ')}</SettingRow>
          <div className="pt-4">
            <button
              onClick={logout}
              className="inline-flex items-center gap-2 rounded-md bg-destructive px-4 py-2 text-sm font-medium text-destructive-foreground transition hover:bg-destructive/90"
            >
              <LogOut className="h-4 w-4" />
              Sign out
            </button>
          </div>
        </SettingsCard>

        <SettingsCard title="Appearance" icon={Palette}>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Moon className="h-5 w-5 text-muted-foreground" />
              <div>
                <p className="font-medium">Theme</p>
                <p className="text-xs text-muted-foreground">Choose your preferred light or dark mode.</p>
              </div>
            </div>
            <select
              value={theme}
              onChange={(e) => setTheme(e.target.value as 'light' | 'dark' | 'system')}
              className="rounded-md border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="light">Light</option>
              <option value="dark">Dark</option>
              <option value="system">System</option>
            </select>
          </div>
        </SettingsCard>

        <SettingsCard title="Notifications" icon={Bell}>
          <SettingsLink
            to="/dashboard/notifications"
            icon={Bell}
            label="Notification preferences"
            description="Choose how you receive in-app and email notifications."
          />
        </SettingsCard>

        {isAdmin && (
          <SettingsCard title="Organization" icon={Building}>
            <div className="space-y-3">
              <SettingsLink
                to="/dashboard/sla"
                icon={Shield}
                label="SLA policies"
                description="Edit response and resolution targets."
              />
              <SettingsLink
                to="/admin/business-calendars"
                icon={Building}
                label="Business calendars"
                description="Manage working hours, timezones, and holidays for SLA."
              />
              <SettingsLink
                to="/admin/users"
                icon={User}
                label="User management"
                description="Manage users and roles."
              />
            </div>
          </SettingsCard>
        )}
      </div>
    </div>
  )
}
