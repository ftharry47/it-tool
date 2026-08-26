import React from 'https://cdn.jsdelivr.net/npm/react@18.3.1/+esm'
import htm from 'https://cdn.jsdelivr.net/npm/htm@3.1.1/+esm'
import { StatusBadge, PriorityBadge } from '../components/Badge.js'

const html = htm.bind(React.createElement)

function formatDate(d) {
  try { return new Date(d).toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }) } catch { return d }
}

export default function TeamMemberModal({ member, tickets, onClose }) {
  if (!member || !tickets) return null

  const memberTickets = tickets.filter(t => String(t['Assigned To']).trim().toLowerCase() === String(member.name).trim().toLowerCase())
  const total = memberTickets.length
  const resolved = memberTickets.filter(t => t['Status'] === 'Resolved')
  const open = memberTickets.filter(t => t['Status'] !== 'Resolved')
  const critical = memberTickets.filter(t => t['Priority'] === 'Critical').length
  const high = memberTickets.filter(t => t['Priority'] === 'High').length

  const resolvedCount = resolved.length
  const resolutionRate = total > 0 ? Math.round((resolvedCount / total) * 100) : 0

  const statusCounts = memberTickets.reduce((acc, t) => {
    const s = t['Status'] || 'Open'
    acc[s] = (acc[s] || 0) + 1
    return acc
  }, {})

  const issueCounts = memberTickets.reduce((acc, t) => {
    const s = t['Issue Type'] || 'Unspecified'
    acc[s] = (acc[s] || 0) + 1
    return acc
  }, {})

  const last7 = Array.from({ length: 7 }, (_, i) => {
    const d = new Date()
    d.setDate(d.getDate() - (6 - i))
    d.setHours(0, 0, 0, 0)
    const day = d.toLocaleDateString('en-US', { weekday: 'short', month: 'numeric', day: 'numeric' })
    const count = resolved.filter(t => {
      const rd = t['Resolved Date'] ? new Date(t['Resolved Date']) : null
      if (!rd) return false
      const rdo = new Date(rd)
      rdo.setHours(0, 0, 0, 0)
      return rdo.getTime() === d.getTime()
    }).length
    return { day, count, max: resolvedCount > 0 ? resolvedCount : 1 }
  })

  const sortedIssues = Object.entries(issueCounts).sort((a, b) => b[1] - a[1])

  const statusColors = { Open: 'bg-amber-500', 'In Progress': 'bg-blue-500', 'On Hold': 'bg-orange-500', Differ: 'bg-sky-500', Resolved: 'bg-emerald-500' }
  const maxActivity = Math.max(...last7.map(d => d.count), 1)

  return html`
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-slate-900/50 backdrop-blur-sm" onClick=${onClose}></div>
      <div className="relative w-full max-w-4xl max-h-[90vh] overflow-y-auto rounded-3xl border border-slate-200 bg-white shadow-xl">
        <div className="sticky top-0 z-10 flex items-center justify-between p-5 border-b border-slate-200 bg-white/95 backdrop-blur">
          <div>
            <p className="text-lg font-bold text-slate-900">${member.name}</p>
            <p className="text-sm text-slate-500">${member.level} • ${member.status}</p>
          </div>
          <button onClick=${onClose} className="w-8 h-8 rounded-full bg-slate-100 text-slate-500 hover:text-slate-900 flex items-center justify-center">×</button>
        </div>

        <div className="p-5 space-y-6">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            ${[
              { l: 'Handled', v: total, c: 'text-slate-900' },
              { l: 'Resolved', v: resolvedCount, c: 'text-emerald-600' },
              { l: 'Open', v: open.length, c: 'text-blue-600' },
              { l: 'Resolution %', v: resolutionRate + '%', c: 'text-slate-900' }
            ].map((s, i) => html`
              <div key=${i} className="rounded-2xl border border-slate-200 bg-slate-50 p-3 text-center">
                <p className="text-xl font-bold ${s.c}">${s.v}</p>
                <p className="text-[10px] text-slate-500 uppercase tracking-wider">${s.l}</p>
              </div>
            `)}
          </div>

          <div className="grid lg:grid-cols-2 gap-5">
            <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 space-y-3">
              <p className="text-sm font-medium text-slate-800">Status Distribution</p>
              ${Object.entries(statusCounts).map(([s, c], i) => html`
                <div key=${i} className="space-y-1">
                  <div className="flex justify-between text-xs text-slate-600">
                    <span>${s}</span>
                    <span>${c} (${Math.round((c / total) * 100)}%)</span>
                  </div>
                  <div className="h-2 rounded-full bg-slate-100 overflow-hidden">
                    <div className="h-full rounded-full ${statusColors[s] || 'bg-slate-400'}" style=${{ width: Math.round((c / total) * 100) + '%' }}></div>
                  </div>
                </div>
              `)}
            </div>

            <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 space-y-3">
              <p className="text-sm font-medium text-slate-800">Top Issue Types</p>
              ${sortedIssues.slice(0, 6).map(([s, c], i) => html`
                <div key=${i} className="space-y-1">
                  <div className="flex justify-between text-xs text-slate-600">
                    <span className="truncate max-w-[70%]">${s}</span>
                    <span>${c}</span>
                  </div>
                  <div className="h-2 rounded-full bg-slate-100 overflow-hidden">
                    <div className="h-full rounded-full bg-indigo-500" style=${{ width: Math.round((c / total) * 100) + '%' }}></div>
                  </div>
                </div>
              `)}
            </div>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 space-y-3">
            <p className="text-sm font-medium text-slate-800">7-Day Activity (Resolutions)</p>
            <div className="flex items-end gap-2 h-28">
              ${last7.map((d, i) => html`
                <div key=${i} className="flex-1 flex flex-col items-center gap-1 h-full justify-end group">
                  <div className="w-full bg-blue-500 rounded-t-lg transition hover:bg-blue-600" style=${{ height: Math.max((d.count / maxActivity) * 100, 5) + '%' }}></div>
                  <span className="text-[10px] text-slate-500">${d.day}</span>
                </div>
              `)}
            </div>
          </div>

          <div className="space-y-3">
            <p className="text-sm font-medium text-slate-800">Incidents Handled <span className="text-slate-500 text-xs">(${total})</span></p>
            <div className="overflow-x-auto rounded-2xl border border-slate-200">
              <table className="w-full text-sm text-left">
                <thead className="bg-slate-100 text-slate-500 text-xs uppercase tracking-wider">
                  <tr>
                    <th className="px-4 py-3 font-medium">Incident</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">Priority</th>
                    <th className="px-4 py-3 font-medium">Description</th>
                    <th className="px-4 py-3 font-medium">Created</th>
                    <th className="px-4 py-3 font-medium">Resolved</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  ${memberTickets.sort((a, b) => new Date(b['Created Date'] || 0) - new Date(a['Created Date'] || 0)).map((t, i) => html`
                    <tr key=${i} className="hover:bg-slate-50 transition">
                      <td className="px-4 py-3 font-mono text-blue-600">${t['Ticket ID']}</td>
                      <td className="px-4 py-3"><${StatusBadge} status=${t['Status']} /></td>
                      <td className="px-4 py-3"><${PriorityBadge} priority=${t['Priority']} /></td>
                      <td className="px-4 py-3 text-slate-600 max-w-xs truncate">${t['Short Description']}</td>
                      <td className="px-4 py-3 text-slate-500 text-xs">${formatDate(t['Created Date'])}</td>
                      <td className="px-4 py-3 text-slate-500 text-xs">${t['Resolved Date'] ? formatDate(t['Resolved Date']) : '-'}</td>
                    </tr>
                  `)}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
}


