import React from 'https://cdn.jsdelivr.net/npm/react@18.3.1/+esm'
import htm from 'https://cdn.jsdelivr.net/npm/htm@3.1.1/+esm'

const html = htm.bind(React.createElement)

export default function AnalyticsSection({ tickets, stats }) {
  const total = (tickets || []).length || 1

  const byStatus = (tickets || []).reduce((acc, t) => {
    const s = t['Status'] || 'Open'
    acc[s] = (acc[s] || 0) + 1
    return acc
  }, {})

  const byIssue = (tickets || []).reduce((acc, t) => {
    const s = t['Issue Type'] || 'Unspecified'
    acc[s] = (acc[s] || 0) + 1
    return acc
  }, {})

  const statusColors = { Open: 'bg-amber-500', 'In Progress': 'bg-blue-500', 'On Hold': 'bg-orange-500', Differ: 'bg-sky-500', Resolved: 'bg-emerald-500' }

  const sortedIssues = Object.entries(byIssue).sort((a, b) => b[1] - a[1])

  return html`
    <div className="space-y-6">
      <h3 className="text-lg font-semibold text-slate-900">Analytics</h3>

      <div className="space-y-3">
        <p className="text-sm text-slate-500">By Status</p>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 space-y-3 shadow-sm">
          ${Object.entries(byStatus).map(([s, c], i) => html`
            <div key=${i} className="space-y-1">
              <div className="flex justify-between text-xs text-slate-700">
                <span>${s}</span>
                <span className="text-slate-500">${c} (${Math.round((c / total) * 100)}%)</span>
              </div>
              <div className="h-2 rounded-full bg-slate-100 overflow-hidden">
                <div className="h-full rounded-full ${statusColors[s] || 'bg-slate-400'}" style=${{ width: Math.round((c / total) * 100) + '%' }}></div>
              </div>
            </div>
          `)}
        </div>
      </div>

      <div className="space-y-3">
        <p className="text-sm text-slate-500">By Issue Type</p>
        <div className="rounded-2xl border border-slate-200 bg-white p-4 space-y-3 shadow-sm">
          ${sortedIssues.slice(0, 8).map(([s, c], i) => html`
            <div key=${i} className="space-y-1">
              <div className="flex justify-between text-xs text-slate-700">
                <span className="truncate max-w-[70%]">${s}</span>
                <span className="text-slate-500">${c}</span>
              </div>
              <div className="h-2 rounded-full bg-slate-100 overflow-hidden">
                <div className="h-full rounded-full bg-indigo-500" style=${{ width: Math.round((c / total) * 100) + '%' }}></div>
              </div>
            </div>
          `)}
        </div>
      </div>

      ${stats && html`
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-center">
          <div className="rounded-2xl border border-slate-200 bg-white p-3 shadow-sm">
            <p className="text-xl font-bold text-slate-900">${stats.total}</p>
            <p className="text-[10px] text-slate-500 uppercase">Total</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-3 shadow-sm">
            <p className="text-xl font-bold text-amber-600">${stats.open}</p>
            <p className="text-[10px] text-slate-500 uppercase">Open</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-3 shadow-sm">
            <p className="text-xl font-bold text-emerald-600">${stats.resolved}</p>
            <p className="text-[10px] text-slate-500 uppercase">Resolved</p>
          </div>
        </div>
      `}
    </div>
  `
}


