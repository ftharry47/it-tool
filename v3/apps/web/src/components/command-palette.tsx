'use client'

import * as React from 'react'
import { useRouter } from 'next/navigation'
import { Command } from 'cmdk'
import * as Dialog from '@radix-ui/react-dialog'
import { useCommandPalette } from '@/store/command-palette'
import { useAuth, api } from '@/lib/api'
import { cn } from '@/lib/utils'
import {
  Home,
  LayoutDashboard,
  LifeBuoy,
  Search,
  Settings,
  Ticket,
  Workflow,
  Server,
  Moon,
  Sun,
  Plus,
  AlertTriangle,
  Bug,
  Inbox,
} from 'lucide-react'
import { useTheme } from 'next-themes'

interface CommandItem {
  id: string
  label: string
  icon?: React.ReactNode
  shortcut?: string
  action: () => void
}

export function CommandPalette() {
  const router = useRouter()
  const { theme, setTheme, resolvedTheme } = useTheme()
  const { open, setOpen } = useCommandPalette()
  const { token, authed } = useAuth()
  const [search, setSearch] = React.useState('')
  const [issueCommands, setIssueCommands] = React.useState<CommandItem[]>([])
  const [mounted, setMounted] = React.useState(false)

  React.useEffect(() => setMounted(true), [])

  const nav = (path: string) => () => {
    router.push(path)
    setOpen(false)
    setSearch('')
  }
  const toggleTheme = () => {
    setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')
    setOpen(false)
  }
  const create = (type: string) => () => {
    setOpen(false)
    setSearch('')
    window.location.href = `/dashboard/submit?type=${type}`
  }

  const commands: CommandItem[] = React.useMemo(() => {
    return [
      { id: 'home', label: 'Go to Home', icon: <Home className="h-4 w-4" />, action: nav('/') },
      { id: 'dashboard', label: 'Go to Dashboard', icon: <LayoutDashboard className="h-4 w-4" />, action: nav('/dashboard/issues') },
      { id: 'issues', label: 'Go to Issues', icon: <Ticket className="h-4 w-4" />, action: nav('/dashboard/issues') },
      { id: 'workflows', label: 'Go to Workflows', icon: <Workflow className="h-4 w-4" />, action: nav('/dashboard/workflows') },
      { id: 'cmdb', label: 'Go to CMDB', icon: <Server className="h-4 w-4" />, action: nav('/dashboard/cmdb') },
      { id: 'help', label: 'Go to Help Center', icon: <LifeBuoy className="h-4 w-4" />, action: nav('/help-center') },
      { id: 'settings', label: 'Go to Settings', icon: <Settings className="h-4 w-4" />, action: nav('/dashboard/settings') },
      { id: 'theme', label: 'Toggle Theme', icon: mounted && resolvedTheme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />, action: toggleTheme },
    ]
  }, [mounted, resolvedTheme])

  const createCommands: CommandItem[] = React.useMemo(() => {
    return [
      { id: 'new-incident', label: 'New Incident', icon: <AlertTriangle className="h-4 w-4" />, action: create('INCIDENT') },
      { id: 'new-bug', label: 'New Bug', icon: <Bug className="h-4 w-4" />, action: create('BUG') },
      { id: 'new-request', label: 'New Service Request', icon: <Inbox className="h-4 w-4" />, action: create('REQUEST') },
      { id: 'new-ticket', label: 'New Quick Ticket', icon: <Plus className="h-4 w-4" />, action: create('REQUEST') },
    ]
  }, [])

  React.useEffect(() => {
    if (!open || !token) {
      setIssueCommands([])
      return
    }
    const load = async () => {
      const res = await fetch(api('/api/issues'), authed())
      if (res.ok) {
        const data = await res.json()
        const issues = Array.isArray(data) ? data : data.value ?? []
        setIssueCommands(
          issues.slice(0, 20).map((issue: any) => ({
            id: `issue-${issue.id}`,
            label: `${issue.ticketId} ${issue.title}`,
            icon: <Ticket className="h-4 w-4" />,
            action: () => {
              setOpen(false)
              setSearch('')
              window.location.href = `/dashboard/issues?search=${encodeURIComponent(issue.ticketId)}`
            },
          }))
        )
      }
    }
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, token])

  React.useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault()
        setOpen(true)
      }
      if (e.key === 'Escape') {
        setOpen(false)
      }
    }
    document.addEventListener('keydown', down)
    return () => document.removeEventListener('keydown', down)
  }, [setOpen])

  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm" />
        <Dialog.Content
          className="fixed left-1/2 top-1/4 z-50 w-full max-w-xl -translate-x-1/2 rounded-lg border bg-popover p-2 shadow-2xl outline-none"
          onEscapeKeyDown={() => setOpen(false)}
        >
          <Dialog.Title className="sr-only">Command palette</Dialog.Title>
          <Dialog.Description className="sr-only">Search commands, pages and issue IDs</Dialog.Description>
          <Command
            label="Command palette"
            className="[cmdk-root]:w-full"
            loop
            shouldFilter
          >
            <div className="flex items-center gap-2 border-b px-3 py-2">
              <Search className="h-4 w-4 text-muted-foreground" />
              <Command.Input
                value={search}
                onValueChange={setSearch}
                placeholder="Type a command or search..."
                className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              />
              <kbd className="hidden rounded border px-1.5 py-0.5 text-xs font-mono sm:inline-block">ESC</kbd>
            </div>
            <Command.List className="max-h-80 overflow-y-auto p-2">
              <Command.Empty className="py-6 text-center text-sm text-muted-foreground">
                No results found.
              </Command.Empty>
              <Command.Group heading="Navigation" className="text-xs font-medium text-muted-foreground">
                {commands.slice(0, 6).map((command) => (
                  <Command.Item
                    key={command.id}
                    value={command.label}
                    onSelect={command.action}
                    className={cn(
                      'flex cursor-pointer items-center gap-3 rounded px-2 py-2 text-sm text-foreground',
                      'data-[selected=true]:bg-accent data-[selected=true]:text-accent-foreground'
                    )}
                  >
                    {command.icon}
                    <span className="flex-1">{command.label}</span>
                  </Command.Item>
                ))}
              </Command.Group>
              <Command.Group heading="Actions" className="mt-2 text-xs font-medium text-muted-foreground">
                {commands.slice(6).map((command) => (
                  <Command.Item
                    key={command.id}
                    value={command.label}
                    onSelect={command.action}
                    className={cn(
                      'flex cursor-pointer items-center gap-3 rounded px-2 py-2 text-sm text-foreground',
                      'data-[selected=true]:bg-accent data-[selected=true]:text-accent-foreground'
                    )}
                  >
                    {command.icon}
                    <span className="flex-1">{command.label}</span>
                  </Command.Item>
                ))}
              </Command.Group>
              <Command.Group heading="Create" className="mt-2 text-xs font-medium text-muted-foreground">
                {createCommands.map((command) => (
                  <Command.Item
                    key={command.id}
                    value={command.label}
                    onSelect={command.action}
                    className={cn(
                      'flex cursor-pointer items-center gap-3 rounded px-2 py-2 text-sm text-foreground',
                      'data-[selected=true]:bg-accent data-[selected=true]:text-accent-foreground'
                    )}
                  >
                    {command.icon}
                    <span className="flex-1">{command.label}</span>
                  </Command.Item>
                ))}
              </Command.Group>
              {issueCommands.length > 0 && (
                <Command.Group heading="Issues" className="mt-2 text-xs font-medium text-muted-foreground">
                  {issueCommands.map((command) => (
                    <Command.Item
                      key={command.id}
                      value={command.label}
                      onSelect={command.action}
                      className={cn(
                        'flex cursor-pointer items-center gap-3 rounded px-2 py-2 text-sm text-foreground',
                        'data-[selected=true]:bg-accent data-[selected=true]:text-accent-foreground'
                      )}
                    >
                      {command.icon}
                      <span className="flex-1">{command.label}</span>
                    </Command.Item>
                  ))}
                </Command.Group>
              )}
            </Command.List>
          </Command>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
