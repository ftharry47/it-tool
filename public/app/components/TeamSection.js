import React from 'https://cdn.jsdelivr.net/npm/react@18.3.1/+esm'
import htm from 'https://cdn.jsdelivr.net/npm/htm@3.1.1/+esm'

const html = htm.bind(React.createElement)

export default function TeamSection({ team, onMemberClick }) {
  return html`
    <div className="space-y-4">
      <h3 className="text-lg font-semibold text-slate-900">Team Performance</h3>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        ${(team || []).map((m, i) => html`
          <div key=${i} onClick=${() => onMemberClick && onMemberClick(m)} className="rounded-2xl border border-slate-200 bg-white p-4 hover:border-blue-300 hover:shadow-md transition cursor-pointer group shadow-sm">
            <div className="flex items-center justify-between mb-3">
              <div>
                <p className="font-semibold text-slate-900 group-hover:text-blue-600 transition">${m.name}</p>
                <p className="text-xs text-slate-500">${m.level}</p>
              </div>
              <span className="text-xs px-2 py-1 rounded-full bg-slate-100 text-slate-600 border border-slate-200">${m.status}</span>
            </div>
            <div className="grid grid-cols-2 gap-2 text-center text-sm mb-3">
              <div className="rounded-xl bg-slate-50 border border-slate-100 p-2">
                <p className="font-bold text-slate-900">${m.ticketsHandled}</p>
                <p className="text-[10px] text-slate-500 uppercase tracking-wider">Handled</p>
              </div>
              <div className="rounded-xl bg-slate-50 border border-slate-100 p-2">
                <p className="font-bold text-emerald-600">${m.resolved}</p>
                <p className="text-[10px] text-slate-500 uppercase tracking-wider">Resolved</p>
              </div>
              <div className="rounded-xl bg-slate-50 border border-slate-100 p-2">
                <p className="font-bold text-blue-600">${m.openTickets}</p>
                <p className="text-[10px] text-slate-500 uppercase tracking-wider">Open</p>
              </div>
              <div className="rounded-xl bg-slate-50 border border-slate-100 p-2">
                <p className="font-bold text-slate-900">${m.slaCompliance || 100}%</p>
                <p className="text-[10px] text-slate-500 uppercase tracking-wider">SLA</p>
              </div>
            </div>
            <div className="flex flex-wrap gap-2 text-xs">
              <span className="px-2 py-1 rounded-lg bg-slate-100 text-slate-600 border border-slate-200">Load ${m.currentLoad}</span>
              <span className="px-2 py-1 rounded-lg bg-red-50 text-red-600 border border-red-100">Critical ${m.criticalTickets}</span>
              <span className="px-2 py-1 rounded-lg bg-orange-50 text-orange-600 border border-orange-100">High ${m.highTickets}</span>
            </div>
          </div>
        `)}
      </div>
    </div>
  `
}


