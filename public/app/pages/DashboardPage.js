import React, { useState, useEffect } from 'https://cdn.jsdelivr.net/npm/react@18.3.1/+esm'
import htm from 'https://cdn.jsdelivr.net/npm/htm@3.1.1/+esm'
import { Link, useLocation } from 'https://cdn.jsdelivr.net/npm/react-router-dom@6.26.0/+esm'
import { callAPI, setUser } from '../api.js'
import { StatusBadge, PriorityBadge } from '../components/Badge.js'
import TicketModal from '../components/TicketModal.js'
import TeamSection from '../components/TeamSection.js'
import AnalyticsSection from '../components/AnalyticsSection.js'
import TeamMemberModal from '../components/TeamMemberModal.js'
import { OverviewSection, SLASection, ReportsSection, SettingsSection, TicketTrends } from '../components/DashboardSections.js'

const html = htm.bind(React.createElement)

const statusOptions = ['All', 'Open', 'In Progress', 'On Hold', 'Differ', 'Resolved']
const priorityOptions = ['All', 'Critical', 'High', 'Medium', 'Low', 'Pending']

function formatDate(d) {
  try { return new Date(d).toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }) } catch { return d }
}

export default function DashboardPage() {
  const [user, setUser] = useState(() => { try { const u = sessionStorage.getItem('portalUser'); return u ? JSON.parse(u) : null } catch { return null } })
  const [login, setLogin] = useState({ login: '', password: '' })
  const [loginError, setLoginError] = useState('')
  const [data, setData] = useState(null)
  const [team, setTeam] = useState([])
  const [statusFilter, setStatusFilter] = useState('All')
  const [priorityFilter, setPriorityFilter] = useState('All')
  const [search, setSearch] = useState('')
  const [selectedTicket, setSelectedTicket] = useState(null)
  const [selectedMember, setSelectedMember] = useState(null)
  const [loading, setLoading] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const [activeTab, setActiveTab] = useState('overview')
  const { pathname } = useLocation()

  useEffect(() => { if (user) loadData() }, [user, refresh])

  const loadData = async () => {
    setLoading(true)
    try {
      const [dash, t] = await Promise.all([callAPI('getDashboardData'), callAPI('getTeamPerformance')])
      setData(dash)
      setTeam(t || [])
    } catch (e) { console.error('loadData', e) }
    setLoading(false)
  }

  const handleLogin = async (e) => {
    e.preventDefault()
    setLoginError('')
    const r = await callAPI('validateUser', login.login, login.password)
    if (r.success) {
      sessionStorage.setItem('portalUser', JSON.stringify(r))
      setUser(r)
      setActiveTab('overview')
      setLogin({ login: '', password: '' })
    } else {
      setLoginError(r.message)
    }
  }

  const handleLogout = () => {
    sessionStorage.removeItem('portalUser')
    setUser(null)
    setData(null)
    setTeam([])
  }

  const filteredTickets = () => {
    if (!data?.tickets) return []
    return data.tickets.filter(t => {
      const st = String(t['Status'] || 'Open')
      const pr = String(t['Priority'] || 'Pending')
      if (statusFilter !== 'All' && st !== statusFilter) return false
      if (priorityFilter !== 'All' && pr !== priorityFilter) return false
      if (search) {
        const q = search.toLowerCase()
        const id = String(t['Ticket ID'] || '').toLowerCase()
        const desc = String(t['Short Description'] || '').toLowerCase()
        const loc = String(t['Location'] || '').toLowerCase()
        const name = String(t['Name'] || '').toLowerCase()
        if (!id.includes(q) && !desc.includes(q) && !loc.includes(q) && !name.includes(q)) return false
      }
      return true
    }).sort((a, b) => new Date(b['Created Date'] || 0) - new Date(a['Created Date'] || 0))
  }

  const tickets = filteredTickets()
  const stats = data?.stats || {}

  const inputClass = (err) => `w-full rounded-xl border bg-white px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 ${err ? 'border-red-500' : 'border-slate-200'}`

  if (!user) {
    return html`
      <div className="min-h-screen bg-slate-50 text-slate-900 flex items-center justify-center p-4">
        <div className="w-full max-w-md rounded-3xl border border-slate-200 bg-white backdrop-blur-xl p-8 shadow-xl">
          <div className="text-center mb-6">
            <div className="w-12 h-12 mx-auto rounded-xl bg-blue-600 flex items-center justify-center text-xl font-bold mb-4">A</div>
            <h1 className="text-2xl font-bold">IT Dashboard</h1>
            <p className="text-sm text-slate-500 mt-1">Sign in to manage incidents</p>
          </div>
          <form onSubmit=${handleLogin} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1.5">Email or Employee ID</label>
              <input type="text" value=${login.login} onChange=${e => setLogin({ ...login, login: e.target.value })} className=${inputClass(loginError)} placeholder="user@company.com" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1.5">Password</label>
              <input type="password" value=${login.password} onChange=${e => setLogin({ ...login, password: e.target.value })} className=${inputClass(loginError)} placeholder="••••••••" />
            </div>
            ${loginError && html`<p className="text-sm text-red-600">${loginError}</p>`}
            <button type="submit" className="w-full py-3.5 rounded-xl bg-blue-600 text-white hover:bg-blue-700 font-semibold shadow-md hover:shadow-blue-500/20 transition">Sign In</button>
          </form>
          <a href="/" className="block text-center mt-6 text-sm text-slate-500 hover:text-slate-600">Back to incident form</a>
        </div>
      </div>
    `
  }

  return html`
    <div className="min-h-screen bg-slate-50 flex">
      <aside className="w-64 bg-slate-900 text-slate-300 flex flex-col h-screen fixed left-0 top-0 z-30 overflow-y-auto">
        <div className="h-16 flex items-center px-6 border-b border-slate-800">
          <${Link} to="/" className="flex items-center gap-3 text-white font-bold text-lg">
            <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center text-sm font-bold">A</div>
            IT Portal
          </${Link}>
        </div>
        <nav className="flex-1 py-4 px-3 space-y-1">
          <${Link} to="/" className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition ${pathname === '/' ? 'bg-blue-600 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'}">
            <svg xmlns="http://www.w3.org/2000/svg" className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
            Home
          </${Link}>
          <${Link} to="/form" className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition ${pathname === '/form' ? 'bg-blue-600 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'}">
            <svg xmlns="http://www.w3.org/2000/svg" className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            Submit
          </${Link}>
          <${Link} to="/dashboard" className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition ${pathname === '/dashboard' ? 'bg-blue-600 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'}">
            <svg xmlns="http://www.w3.org/2000/svg" className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
            Dashboard
          </${Link}>
        </nav>
        <div className="p-4 border-t border-slate-800">
          <p className="text-xs text-slate-500">Signed in as</p>
          <p className="text-sm text-white font-medium truncate">${user.displayName}</p>
          <p className="text-xs text-blue-400">${user.role}</p>
        </div>
      </aside>
      <main className="flex-1 ml-64 min-h-screen flex flex-col">
      <header className="border-b border-slate-200 bg-white/80 backdrop-blur-xl sticky top-0 z-20">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <div>
            <h1 className="text-xl font-semibold tracking-tight text-slate-900">IT Dashboard</h1>
            <p className="text-xs text-slate-500">Modern incident management</p>
          </div>
          <div className="flex items-center gap-4">
            <div className="text-right hidden sm:block">
              <p className="text-sm font-medium text-slate-800">${user.displayName}</p>
              <p className="text-xs text-slate-500">${user.role}</p>
            </div>
            <button onClick=${handleLogout} className="px-4 py-2 rounded-xl border border-slate-200 text-sm font-medium text-slate-600 hover:bg-slate-50 transition">Logout</button>
          </div>
        </div>
      </header>

      <div className="flex-1 overflow-y-auto p-4 sm:p-6">
        <div className="max-w-7xl mx-auto space-y-6">
          <div className="rounded-2xl border border-slate-200 bg-white p-1.5 shadow-sm flex flex-wrap gap-1">
            ${[
              { id: 'overview', label: 'Overview', icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6' },
              { id: 'tickets', label: 'Tickets', icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2' },
              { id: 'team', label: 'Team', icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z' },
              { id: 'sla', label: 'SLA', icon: 'M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z' },
              { id: 'analytics', label: 'Analytics', icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6M13 19v-2a2 2 0 012-2h2a2 2 0 012 2v2M9 11V7a2 2 0 012-2h2a2 2 0 012 2v4' },
              { id: 'reports', label: 'Reports', icon: 'M9 17v-2a2 2 0 012-2h2a2 2 0 012 2v2M13 7V5a2 2 0 012-2h2a2 2 0 012 2v2M9 11V9a2 2 0 012-2h2a2 2 0 012 2v2M19 13V5a2 2 0 00-2-2H7a2 2 0 00-2 2v8' },
              { id: 'settings', label: 'Settings', icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M12 15a3 3 0 100-6 3 3 0 000 6z' }
            ].map((t, i) => html`
              <button key=${i} onClick=${() => setActiveTab(t.id)} className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition ${activeTab === t.id ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100'}">
                <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d=${t.icon} /></svg>
                ${t.label}
              </button>
            `)}
          </div>

          ${activeTab === 'overview' && html`
            <${OverviewSection} data=${data} team=${team} onSelectTicket=${setSelectedTicket} />
          `}

          ${activeTab === 'tickets' && html`
            <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-xl">
              <div className="flex flex-col sm:flex-row gap-3 mb-4">
                <input type="text" value=${search} onChange=${e => setSearch(e.target.value)} placeholder="Search incidents..." className="flex-1 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-900 outline-none focus:border-blue-500" />
                <select value=${statusFilter} onChange=${e => setStatusFilter(e.target.value)} className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-900 focus:border-blue-500">
                  ${statusOptions.map(s => html`<option value=${s} key=${s}>Status: ${s}</option>`)}
                </select>
                <select value=${priorityFilter} onChange=${e => setPriorityFilter(e.target.value)} className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-900 focus:border-blue-500">
                  ${priorityOptions.map(p => html`<option value=${p} key=${p}>Priority: ${p}</option>`)}
                </select>
                <button onClick=${() => setRefresh(r => r + 1)} className="px-4 py-2.5 rounded-xl border border-slate-200 text-sm font-medium text-slate-600 hover:bg-slate-50">Refresh</button>
              </div>

              <div className="overflow-x-auto rounded-2xl border border-slate-200">
                <table className="w-full text-sm text-left">
                  <thead className="bg-slate-100 text-slate-500 text-xs uppercase tracking-wider">
                    <tr>
                      <th className="px-4 py-3 font-medium">Incident</th>
                      <th className="px-4 py-3 font-medium">Requester</th>
                      <th className="px-4 py-3 font-medium">Location</th>
                      <th className="px-4 py-3 font-medium">Status</th>
                      <th className="px-4 py-3 font-medium">Priority</th>
                      <th className="px-4 py-3 font-medium">Created</th>
                      <th className="px-4 py-3 font-medium">Assigned</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    ${tickets.length ? tickets.map(t => html`
                      <tr key=${t['Ticket ID']} onClick=${() => setSelectedTicket(t)} className="hover:bg-slate-50 cursor-pointer transition">
                        <td className="px-4 py-3 font-mono text-blue-600">${t['Ticket ID']}</td>
                        <td className="px-4 py-3 text-slate-800">${t['Name']}</td>
                        <td className="px-4 py-3 text-slate-600">${t['Location']}</td>
                        <td className="px-4 py-3"><${StatusBadge} status=${t['Status']} /></td>
                        <td className="px-4 py-3"><${PriorityBadge} priority=${t['Priority']} /></td>
                        <td className="px-4 py-3 text-slate-500 text-xs">${formatDate(t['Created Date'])}</td>
                        <td className="px-4 py-3 text-slate-600">${t['Assigned To'] || 'Unassigned'}</td>
                      </tr>
                    `) : html`
                      <tr>
                        <td colspan="7" className="px-4 py-8 text-center text-slate-500">No incidents match the current filters.</td>
                      </tr>
                    `}
                  </tbody>
                </table>
              </div>
            </div>
          `}

          ${activeTab === 'team' && html`
            <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-xl">
              <${TeamSection} team=${team} onMemberClick=${setSelectedMember} />
            </div>
          `}

          ${activeTab === 'sla' && html`
            <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-xl">
              <${SLASection} stats=${stats} team=${team} />
            </div>
          `}

          ${activeTab === 'analytics' && html`
            <div className="grid lg:grid-cols-3 gap-6">
              <div className="lg:col-span-2 rounded-3xl border border-slate-200 bg-white p-5 shadow-xl">
                <${AnalyticsSection} tickets=${data?.tickets || []} stats=${stats} />
              </div>
              <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-xl">
                <${TicketTrends} tickets=${data?.tickets || []} />
              </div>
            </div>
          `}

          ${activeTab === 'reports' && html`
            <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-xl">
              <${ReportsSection} user=${user} />
            </div>
          `}

          ${activeTab === 'settings' && html`
            <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-xl">
              <${SettingsSection} settings=${data?.settings} user=${user} onChange=${() => setRefresh(r => r + 1)} />
            </div>
          `}

          ${loading && html`<div className="text-center py-4"><div className="w-6 h-6 border-2 border-slate-300 border-t-blue-600 rounded-full animate-spin mx-auto"></div><p className="text-xs text-slate-500 mt-2">Loading...</p></div>`}
        </div>
      </div>
      </main>

      ${selectedTicket && html`<${TicketModal} ticket=${selectedTicket} onClose=${() => setSelectedTicket(null)} onUpdate=${() => { setSelectedTicket(null); setRefresh(r => r + 1) }} user=${user} staff=${data?.itStaff || []} statuses=${statusOptions.slice(1)} priorities=${priorityOptions.slice(1)} config=${data?.config} />`}
      ${selectedMember && html`<${TeamMemberModal} member=${selectedMember} tickets=${data?.tickets || []} onClose=${() => setSelectedMember(null)} />`}
    </div>
  `
}


