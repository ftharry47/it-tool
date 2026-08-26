import React, { useState, useEffect } from 'https://cdn.jsdelivr.net/npm/react@18.3.1/+esm'
import htm from 'https://cdn.jsdelivr.net/npm/htm@3.1.1/+esm'
import { callAPI } from '../api.js'
import { StatusBadge, PriorityBadge } from './Badge.js'

const html = htm.bind(React.createElement)

const statusColors = {
  Open: 'bg-amber-500',
  'In Progress': 'bg-blue-500',
  'On Hold': 'bg-orange-500',
  Differ: 'bg-sky-500',
  Resolved: 'bg-emerald-500'
}

function formatDate(d) {
  try { return new Date(d).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) } catch { return d }
}

function formatDay(d) {
  try { return new Date(d).toLocaleDateString('en-US', { weekday: 'short' }) } catch { return d }
}

export function StaffSection({ staff }) {
  const sorted = (staff || []).slice().sort((a, b) => (a.name || '').localeCompare(b.name || ''))
  return html`
    <div className="space-y-4">
      <h3 className="text-lg font-semibold text-slate-900">Staff Availability</h3>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        ${sorted.length ? sorted.map((s, i) => html`
          <div key=${i} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm flex items-center gap-3">
            <div className="relative">
              <div className="w-10 h-10 rounded-full bg-slate-100 text-slate-700 flex items-center justify-center font-semibold text-sm">
                ${(s.name || 'U').split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase()}
              </div>
              <span className="absolute bottom-0 right-0 w-3 h-3 rounded-full border-2 border-white ${s.isAvailable ? 'bg-emerald-500' : s.status === 'Break' ? 'bg-amber-500' : 'bg-slate-400'}"></span>
            </div>
            <div className="min-w-0 flex-1">
              <p className="font-semibold text-slate-900 truncate">${s.name}</p>
              <p className="text-xs text-slate-500">${s.level} • ${s.status}</p>
            </div>
          </div>
        `) : html`<p className="text-sm text-slate-500 col-span-full">No staff data available.</p>`}
      </div>
    </div>
  `
}

export function RecentTickets({ tickets, onSelect }) {
  const recent = (tickets || []).slice().sort((a, b) => new Date(b['Created Date'] || 0) - new Date(a['Created Date'] || 0)).slice(0, 6)
  return html`
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-slate-900">Recent Incidents</h3>
        <span className="text-xs text-slate-500">Last 6 tickets</span>
      </div>
      <div className="space-y-3">
        ${recent.length ? recent.map((t, i) => html`
          <div key=${i} onClick=${() => onSelect && onSelect(t)} className="rounded-2xl border border-slate-200 bg-white p-4 hover:border-blue-300 hover:shadow-md transition cursor-pointer shadow-sm">
            <div className="flex items-center justify-between gap-4 mb-2">
              <div className="flex items-center gap-3 min-w-0">
                <span className="font-mono text-sm text-blue-600">${t['Ticket ID']}</span>
                <p className="text-sm font-medium text-slate-900 truncate">${t['Short Description'] || 'No description'}</p>
              </div>
              <${StatusBadge} status=${t['Status']} />
            </div>
            <div className="flex items-center gap-4 text-xs text-slate-500">
              <span className="truncate">${t['Name']}</span>
              <span>•</span>
              <span className="truncate">${t['Location']}</span>
              <span>•</span>
              <span>${formatDate(t['Created Date'])}</span>
            </div>
          </div>
        `) : html`<p className="text-sm text-slate-500">No incidents yet.</p>`}
      </div>
    </div>
  `
}

export function TicketTrends({ tickets }) {
  const days = []
  const today = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    days.push(d)
  }
  const counts = days.map(d => (tickets || []).filter(t => {
    const cd = t['Created Date']
    if (!cd) return false
    try { return new Date(cd).toDateString() === d.toDateString() } catch { return false }
  }).length)
  const max = Math.max(...counts, 1)
  return html`
    <div className="space-y-4">
      <h3 className="text-lg font-semibold text-slate-900">7-Day Ticket Trend</h3>
      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-end justify-between gap-2 h-40">
          ${counts.map((c, i) => html`
            <div key=${i} className="flex-1 flex flex-col items-center gap-2">
              <div className="w-full bg-slate-100 rounded-t-lg relative h-28 overflow-hidden">
                <div className="absolute bottom-0 left-0 right-0 bg-blue-500 rounded-t-lg transition-all" style=${{ height: Math.round((c / max) * 100) + '%' }}></div>
              </div>
              <span className="text-[10px] text-slate-500 font-medium">${formatDay(days[i])}</span>
              <span className="text-xs font-semibold text-slate-700">${c}</span>
            </div>
          `)}
        </div>
      </div>
    </div>
  `
}

export function OverviewSection({ data, team, onSelectTicket }) {
  const stats = data?.stats || {}
  const cards = [
    { label: 'Total', value: stats.total || 0, color: 'text-slate-900' },
    { label: 'Open', value: stats.open || 0, color: 'text-amber-600' },
    { label: 'In Progress', value: stats.inProgress || 0, color: 'text-blue-600' },
    { label: 'On Hold', value: stats.onHold || 0, color: 'text-orange-600' },
    { label: 'Differ', value: stats.differ || 0, color: 'text-sky-600' },
    { label: 'Resolved', value: stats.resolved || 0, color: 'text-emerald-600' }
  ]
  return html`
    <div className="space-y-6">
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        ${cards.map((s, i) => html`
          <div key=${i} className="rounded-2xl border border-slate-200 bg-white p-4 text-center shadow-sm hover:border-slate-300 transition">
            <p className="text-2xl font-bold ${s.color}">${s.value}</p>
            <p className="text-[10px] text-slate-500 uppercase tracking-wider">${s.label}</p>
          </div>
        `)}
      </div>

      <div className="grid lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <${RecentTickets} tickets=${data?.tickets} onSelect=${onSelectTicket} />
        </div>
        <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <${TicketTrends} tickets=${data?.tickets} />
        </div>
      </div>

      <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <${StaffSection} staff=${data?.itStaff || team} />
      </div>
    </div>
  `
}

export function SLASection({ stats, team }) {
  const targets = [
    { priority: 'Critical', response: '1 hour', resolution: '4 hours' },
    { priority: 'High', response: '4 hours', resolution: '24 hours' },
    { priority: 'Medium', response: '8 hours', resolution: '72 hours' },
    { priority: 'Low', response: '24 hours', resolution: '168 hours' }
  ]
  const avg = team && team.length ? team.reduce((a, m) => a + (m.slaCompliance || 100), 0) / team.length : 0
  return html`
    <div className="space-y-6">
      <h3 className="text-lg font-semibold text-slate-900">SLA Targets & Compliance</h3>

      <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm text-center">
          <p className="text-3xl font-bold text-emerald-600">${avg.toFixed(0)}%</p>
          <p className="text-xs text-slate-500 uppercase tracking-wider">Avg SLA Compliance</p>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm text-center">
          <p className="text-3xl font-bold text-red-600">${stats.critical || 0}</p>
          <p className="text-xs text-slate-500 uppercase tracking-wider">Critical Tickets</p>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm text-center">
          <p className="text-3xl font-bold text-amber-600">${stats.highPriority || 0}</p>
          <p className="text-xs text-slate-500 uppercase tracking-wider">High Priority</p>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm text-center">
          <p className="text-3xl font-bold text-blue-600">${stats.resolvedToday || 0}</p>
          <p className="text-xs text-slate-500 uppercase tracking-wider">Resolved Today</p>
        </div>
      </div>

      <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm overflow-x-auto">
        <table className="w-full text-sm text-left">
          <thead className="bg-slate-50 text-slate-500 text-xs uppercase tracking-wider">
            <tr>
              <th className="px-4 py-3 font-medium">Priority</th>
              <th className="px-4 py-3 font-medium">Response Target</th>
              <th className="px-4 py-3 font-medium">Resolution Target</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            ${targets.map((t, i) => html`
              <tr key=${i}>
                <td className="px-4 py-3"><${PriorityBadge} priority=${t.priority} /></td>
                <td className="px-4 py-3 text-slate-700">${t.response}</td>
                <td className="px-4 py-3 text-slate-700">${t.resolution}</td>
              </tr>
            `)}
          </tbody>
        </table>
      </div>

      <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <h4 className="text-sm font-semibold text-slate-900 mb-4">Per-Member SLA Compliance</h4>
        <div className="space-y-3">
          ${(team || []).map((m, i) => html`
            <div key=${i} className="space-y-1">
              <div className="flex justify-between text-xs text-slate-700">
                <span>${m.name}</span>
                <span className="text-slate-500">${m.slaCompliance || 100}%</span>
              </div>
              <div className="h-2 rounded-full bg-slate-100 overflow-hidden">
                <div className="h-full rounded-full ${(m.slaCompliance || 100) >= 90 ? 'bg-emerald-500' : (m.slaCompliance || 100) >= 70 ? 'bg-amber-500' : 'bg-red-500'}" style=${{ width: (m.slaCompliance || 100) + '%' }}></div>
              </div>
            </div>
          `)}
        </div>
      </div>
    </div>
  `
}

export function ReportsSection({ user }) {
  const [msg, setMsg] = useState('')
  const isViewer = user?.role === 'Viewer'
  const generate = async (type) => {
    if (isViewer) { setMsg('Viewers cannot generate reports'); return }
    try {
      const r = await callAPI('generateReport', type)
      if (r?.success && r.csvContent) {
        const blob = new Blob([r.csvContent], { type: 'text/csv;charset=utf-8;' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = r.fileName || `${type}-report.csv`
        document.body.appendChild(a)
        a.click()
        a.remove()
        URL.revokeObjectURL(url)
        setMsg('Report downloaded')
      } else {
        setMsg(r?.error || 'Failed to generate report')
      }
    } catch (e) { setMsg('Error: ' + e.message) }
  }
  const reports = [
    { id: 'tickets', title: 'All Tickets', desc: 'Complete ticket export', icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2' },
    { id: 'team', title: 'Team Performance', desc: 'Monthly team metrics', icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0z' },
    { id: 'sla', title: 'SLA Report', desc: 'SLA compliance details', icon: 'M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z' },
    { id: 'monthly', title: 'Monthly Summary', desc: 'Tickets per month', icon: 'M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z' }
  ]
  return html`
    <div className="space-y-6">
      <h3 className="text-lg font-semibold text-slate-900">Reports</h3>
      ${msg && html`<p className="text-sm ${msg.startsWith('Error') || msg.startsWith('Viewers') ? 'text-red-600' : 'text-emerald-600'}">${msg}</p>`}
      <div className="grid md:grid-cols-2 gap-4">
        ${reports.map((r, i) => html`
          <div key=${i} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm hover:border-blue-300 transition">
            <div className="flex items-start justify-between mb-3">
              <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
                <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d=${r.icon} /></svg>
              </div>
              <button onClick=${() => generate(r.id)} disabled=${isViewer} className="px-3 py-1.5 rounded-lg text-xs font-medium ${isViewer ? 'bg-slate-100 text-slate-400 cursor-not-allowed' : 'bg-blue-600 text-white hover:bg-blue-700'}">Download CSV</button>
            </div>
            <h4 className="font-semibold text-slate-900">${r.title}</h4>
            <p className="text-xs text-slate-500 mt-1">${r.desc}</p>
          </div>
        `)}
      </div>
    </div>
  `
}

export function SettingsSection({ settings, user, onChange }) {
  const [local, setLocal] = useState({ AUTO_ASSIGN: false, DRY_RUN: false, AUTO_SIGN_OUT: true })
  const [emails, setEmails] = useState({ PRIMARY_ADMIN_EMAIL: '', SECONDARY_ADMIN_EMAIL: '', TERTIARY_ADMIN_EMAIL: '', ESCALATION_EMAIL: '', CRITICAL_EMAIL: '' })
  const [msg, setMsg] = useState('')
  const [loading, setLoading] = useState(false)
  const isViewer = user?.role === 'Viewer'

  useEffect(() => {
    if (settings) {
      setLocal(prev => ({
        ...prev,
        AUTO_ASSIGN: !!settings.AUTO_ASSIGN,
        DRY_RUN: !!settings.DRY_RUN,
        AUTO_SIGN_OUT: settings.AUTO_SIGN_OUT !== undefined ? !!settings.AUTO_SIGN_OUT : true
      }))
      setEmails(prev => ({
        ...prev,
        PRIMARY_ADMIN_EMAIL: settings.PRIMARY_ADMIN_EMAIL || prev.PRIMARY_ADMIN_EMAIL || '',
        SECONDARY_ADMIN_EMAIL: settings.SECONDARY_ADMIN_EMAIL || prev.SECONDARY_ADMIN_EMAIL || '',
        TERTIARY_ADMIN_EMAIL: settings.TERTIARY_ADMIN_EMAIL || prev.TERTIARY_ADMIN_EMAIL || '',
        ESCALATION_EMAIL: settings.ESCALATION_EMAIL || prev.ESCALATION_EMAIL || '',
        CRITICAL_EMAIL: settings.CRITICAL_EMAIL || prev.CRITICAL_EMAIL || ''
      }))
    }
  }, [settings])

  const toggle = async (key) => {
    if (isViewer) { setMsg('Viewers cannot change settings'); return }
    const next = { ...local, [key]: !local[key] }
    setLocal(next)
    setLoading(true)
    try {
      const r = await callAPI('setSetting', key, next[key])
      if (r?.success) { setMsg(`${key} updated`); onChange && onChange() } else setMsg(r?.error || 'Failed')
    } catch (e) { setMsg('Error: ' + e.message) }
    setLoading(false)
  }

  const saveEmail = async (key) => {
    if (isViewer) { setMsg('Viewers cannot change settings'); return }
    const value = emails[key]
    if (!value) { setMsg('Enter an email'); return }
    setLoading(true)
    try {
      const r = await callAPI('setSetting', key, value)
      if (r?.success) { setMsg(`${key} saved`); onChange && onChange() } else setMsg(r?.error || 'Failed')
    } catch (e) { setMsg('Error: ' + e.message) }
    setLoading(false)
  }

  const toggleRow = (key, label, desc) => html`
    <div className="flex items-center justify-between p-4 rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div>
        <h4 className="font-semibold text-slate-900">${label}</h4>
        <p className="text-xs text-slate-500">${desc}</p>
      </div>
      <button onClick=${() => toggle(key)} disabled=${isViewer || loading} className="relative w-14 h-7 rounded-full transition ${local[key] ? 'bg-blue-600' : 'bg-slate-300'}">
        <span className="absolute top-1 left-1 w-5 h-5 rounded-full bg-white transition transform ${local[key] ? 'translate-x-7' : 'translate-x-0'}"></span>
      </button>
    </div>
  `

  return html`
    <div className="space-y-6">
      <h3 className="text-lg font-semibold text-slate-900">Settings</h3>
      ${msg && html`<p className="text-sm ${msg.startsWith('Error') || msg.startsWith('Viewers') ? 'text-red-600' : 'text-emerald-600'}">${msg}</p>`}

      <div className="grid md:grid-cols-2 gap-4">
        ${toggleRow('AUTO_ASSIGN', 'Auto-Assign Tickets', 'Auto-assign new tickets to available L1 staff')}
        ${toggleRow('DRY_RUN', 'Dry-Run Mode', 'Log emails instead of sending them')}
        ${toggleRow('AUTO_SIGN_OUT', 'Auto Sign Out', 'Sign out after 15 minutes inactivity')}
      </div>

      <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <h4 className="text-sm font-semibold text-slate-900">Notification Emails</h4>
        <p className="text-xs text-slate-500">Recipients for admin, escalation and critical alerts.</p>
        <div className="grid md:grid-cols-2 gap-4">
          ${Object.entries(emails).map(([key, value], i) => html`
            <div key=${i}>
              <label className="block text-xs font-medium text-slate-500 mb-1.5">${key.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())}</label>
              <div className="flex gap-2">
                <input type="email" value=${value} onChange=${e => setEmails({ ...emails, [key]: e.target.value })} disabled=${isViewer} placeholder="email@company.com" className="flex-1 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 disabled:bg-slate-50" />
                <button onClick=${() => saveEmail(key)} disabled=${isViewer || loading} className="px-3 py-2 rounded-xl text-xs font-medium ${isViewer ? 'bg-slate-100 text-slate-400' : 'bg-blue-600 text-white hover:bg-blue-700'}">Save</button>
              </div>
            </div>
          `)}
        </div>
      </div>
    </div>
  `
}
