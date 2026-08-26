import React from 'https://cdn.jsdelivr.net/npm/react@18.3.1/+esm'
import htm from 'https://cdn.jsdelivr.net/npm/htm@3.1.1/+esm'

const html = htm.bind(React.createElement)

const statusColors = {
  Open: { bg: 'bg-amber-100', text: 'text-amber-700', border: 'border-amber-200' },
  'In Progress': { bg: 'bg-blue-100', text: 'text-blue-700', border: 'border-blue-200' },
  'On Hold': { bg: 'bg-orange-100', text: 'text-orange-700', border: 'border-orange-200' },
  Differ: { bg: 'bg-sky-100', text: 'text-sky-700', border: 'border-sky-200' },
  Resolved: { bg: 'bg-emerald-100', text: 'text-emerald-700', border: 'border-emerald-200' }
}

const priorityColors = {
  Critical: { bg: 'bg-red-100', text: 'text-red-700', border: 'border-red-200' },
  High: { bg: 'bg-orange-100', text: 'text-orange-700', border: 'border-orange-200' },
  Medium: { bg: 'bg-yellow-100', text: 'text-yellow-700', border: 'border-yellow-200' },
  Low: { bg: 'bg-green-100', text: 'text-green-700', border: 'border-green-200' },
  Pending: { bg: 'bg-slate-100', text: 'text-slate-700', border: 'border-slate-200' }
}

export function StatusBadge({ status }) {
  const c = statusColors[status] || statusColors.Open
  return html`<span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${c.bg} ${c.text} ${c.border}">${status || 'Open'}</span>`
}

export function PriorityBadge({ priority }) {
  const c = priorityColors[priority] || priorityColors.Pending
  return html`<span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${c.bg} ${c.text} ${c.border}">${priority || 'Pending'}</span>`
}


