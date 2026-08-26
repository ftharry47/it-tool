import React, { useState, useEffect } from 'https://cdn.jsdelivr.net/npm/react@18.3.1/+esm'
import htm from 'https://cdn.jsdelivr.net/npm/htm@3.1.1/+esm'
import { Link } from 'https://cdn.jsdelivr.net/npm/react-router-dom@6.26.0/+esm'
import { callAPI } from '../api.js'
import { StatusBadge, PriorityBadge } from '../components/Badge.js'

const html = htm.bind(React.createElement)

function FeatureCard({ icon, title, description, to, color }) {
  const colorMap = {
    blue: 'bg-blue-50 text-blue-600 border-blue-500/10',
    emerald: 'bg-emerald-500/10 text-emerald-600 border-emerald-500/10',
    violet: 'bg-violet-500/10 text-violet-600 border-violet-500/10'
  }
  return html`
    <div className="group rounded-3xl border border-slate-200 bg-white p-6 transition duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-blue-500/10 hover:border-slate-300">
      <div className="w-12 h-12 rounded-2xl ${colorMap[color]} flex items-center justify-center mb-4 border transition group-hover:scale-110 duration-300">
        ${icon}
      </div>
      <h3 className="text-xl font-semibold text-slate-900 mb-2">${title}</h3>
      <p className="text-slate-500 text-sm mb-4">${description}</p>
      <${Link} to=${to} className="inline-flex items-center gap-2 text-sm font-medium text-blue-600 hover:text-blue-600 transition group/link">
        Get started
        <svg className="w-4 h-4 transition group-hover/link:translate-x-1" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
      </${Link}>
    </div>
  `
}

export default function HomePage() {
  const [stats, setStats] = useState({ total: 0, open: 0, resolved: 0 })
  const [trackId, setTrackId] = useState('')
  const [trackResult, setTrackResult] = useState(null)

  useEffect(() => {
    callAPI('getDashboardData').then(data => {
      if (data && data.stats) setStats(data.stats)
    }).catch(() => {})
  }, [])

  const handleTrack = async (e) => {
    e.preventDefault()
    if (!trackId.trim()) return
    try {
      const r = await callAPI('getTicketByIdForTracking', trackId.trim())
      setTrackResult(r)
    } catch (e) { setTrackResult({ success: false, error: e.message }) }
  }

  return html`
    <div className="min-h-screen bg-slate-50 text-slate-900 font-[Inter] overflow-x-hidden">
      <nav className="fixed top-0 left-0 right-0 z-50 border-b border-slate-200 bg-slate-50/80 backdrop-blur-xl">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <${Link} to="/" className="flex items-center gap-2 text-slate-900 font-bold text-lg tracking-tight">
            <svg className="w-7 h-7 text-blue-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M9 12l2 2 4-4"/></svg>
            IT Support Portal
          </${Link}>
          <div className="hidden md:flex items-center gap-8 text-sm font-medium text-slate-500">
            <${Link} to="/" className="hover:text-slate-900 transition">Home</${Link}>
            <${Link} to="/form" className="hover:text-slate-900 transition">Submit</${Link}>
            <${Link} to="/dashboard" className="hover:text-slate-900 transition">Dashboard</${Link}>
          </div>
          <div className="flex items-center gap-3 md:hidden">
            <${Link} to="/form" className="text-sm font-medium text-slate-600 hover:text-slate-900">Submit</${Link}>
            <${Link} to="/dashboard" className="text-sm font-medium text-slate-600 hover:text-slate-900">Dashboard</${Link}>
          </div>
        </div>
      </nav>

      <section className="relative pt-32 pb-20 lg:pt-48 lg:pb-32 overflow-hidden">
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          <div className="absolute top-20 -left-20 w-[28rem] h-[28rem] rounded-full bg-blue-600/20 blur-[100px] animate-pulse-glow animate-float"></div>
          <div className="absolute bottom-10 right-0 w-[26rem] h-[26rem] rounded-full bg-violet-600/15 blur-[100px] animate-pulse-glow animate-float-delay"></div>
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[36rem] h-[36rem] rounded-full bg-indigo-600/10 blur-[120px]"></div>
        </div>

        <div className="relative max-w-7xl mx-auto px-6">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            <div className="space-y-8">
              <div className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 animate-fade-in-up">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                Modern Incident Management
              </div>
              <h1 className="text-5xl md:text-6xl lg:text-7xl font-bold tracking-tight animate-fade-in-up animate-fade-in-up-delay-1">
                <span className="bg-clip-text text-transparent bg-gradient-to-r from-blue-400 via-indigo-400 to-violet-400 animate-gradient">Resolve IT issues</span>
                <br />
                <span className="text-slate-900">faster than ever.</span>
              </h1>
              <p className="text-lg md:text-xl text-slate-500 max-w-xl animate-fade-in-up animate-fade-in-up-delay-2">
                A professional support portal for submitting, tracking, and managing incidents with real-time analytics and team performance insights.
              </p>
              <div className="flex flex-wrap gap-4 animate-fade-in-up animate-fade-in-up-delay-3">
                <${Link} to="/form" className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-blue-600 text-white font-semibold text-sm shadow-lg shadow-blue-600/25 hover:bg-blue-500 hover:scale-105 transition duration-300">
                  <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 8v8M8 12h8"/></svg>
                  Submit Incident
                </${Link}>
                <${Link} to="/dashboard" className="inline-flex items-center gap-2 px-6 py-3 rounded-xl border border-slate-200 bg-white text-slate-900 font-semibold text-sm hover:bg-slate-100 hover:border-slate-300 hover:scale-105 transition duration-300">
                  Open Dashboard
                  <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
                </${Link}>
              </div>

              <div className="flex gap-8 pt-4 animate-fade-in-up animate-fade-in-up-delay-4">
                <div>
                  <p className="text-2xl font-bold text-slate-900">${stats.total}</p>
                  <p className="text-xs text-slate-500 uppercase tracking-wider">Total</p>
                </div>
                <div>
                  <p className="text-2xl font-bold text-amber-600">${stats.open}</p>
                  <p className="text-xs text-slate-500 uppercase tracking-wider">Open</p>
                </div>
                <div>
                  <p className="text-2xl font-bold text-emerald-600">${stats.resolved}</p>
                  <p className="text-xs text-slate-500 uppercase tracking-wider">Resolved</p>
                </div>
              </div>

              <form onSubmit=${handleTrack} className="mt-8 max-w-md animate-fade-in-up animate-fade-in-up-delay-4">
                <p className="text-sm font-medium text-slate-700 mb-2">Track your incident</p>
                <div className="flex gap-2">
                  <input type="text" value=${trackId} onChange=${e => setTrackId(e.target.value)} placeholder="Enter INC number..." className="flex-1 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-900 outline-none focus:border-blue-500" />
                  <button type="submit" className="px-4 py-2.5 rounded-xl bg-blue-600 text-white text-sm font-medium hover:bg-blue-700">Track</button>
                </div>
                ${trackResult && html`
                  <div className="mt-3 rounded-xl border ${trackResult.success ? 'border-emerald-200 bg-emerald-50' : 'border-red-200 bg-red-50'} p-3 text-sm">
                    ${trackResult.success ? html`
                      <div className="space-y-1">
                        <div className="flex items-center justify-between">
                          <span className="font-mono text-slate-900">${trackResult.ticket?.['Ticket ID']}</span>
                          <${StatusBadge} status=${trackResult.ticket?.['Status']} />
                        </div>
                        <p className="text-slate-700">${trackResult.ticket?.['Short Description']}</p>
                        <div className="flex items-center gap-2 text-xs text-slate-500">
                          <span>${trackResult.ticket?.['Location']}</span>
                          <span>•</span>
                          <span>${trackResult.ticket?.['Assigned To'] || 'Unassigned'}</span>
                          <span>•</span>
                          <${PriorityBadge} priority=${trackResult.ticket?.['Priority']} />
                        </div>
                      </div>
                    ` : html`<p className="text-red-700">${trackResult.error || 'Ticket not found'}</p>`}
                  </div>
                `}
              </form>
            </div>

            <div className="relative hidden lg:block animate-fade-in-up animate-fade-in-up-delay-2">
              <div className="relative rounded-3xl border border-slate-200 bg-white/80 backdrop-blur-2xl p-6 shadow-xl shadow-black/40 animate-float">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-full bg-red-500"></div>
                    <div className="w-3 h-3 rounded-full bg-amber-500"></div>
                    <div className="w-3 h-3 rounded-full bg-emerald-500"></div>
                  </div>
                  <span className="text-xs text-slate-500 font-mono">INC-0001</span>
                </div>
                <div className="space-y-3">
                  <div className="h-2.5 w-3/4 rounded-full bg-slate-100"></div>
                  <div className="h-2.5 w-1/2 rounded-full bg-slate-100"></div>
                  <div className="h-24 rounded-2xl bg-slate-100/70 border border-slate-100 p-4">
                    <div className="flex items-center gap-3 mb-2">
                      <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center text-xs font-bold">IT</div>
                      <div>
                        <p className="text-sm font-medium text-slate-800">Printer not responding</p>
                        <p className="text-xs text-slate-500">Richmond, VA</p>
                      </div>
                    </div>
                    <span className="inline-flex items-center rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-600">Open</span>
                  </div>
                </div>
              </div>
              <div className="absolute -bottom-6 -right-6 w-48 rounded-2xl border border-slate-200 bg-white/90 p-4 shadow-xl animate-float-delay">
                <p className="text-xs text-slate-500 uppercase tracking-wider mb-1">Resolved today</p>
                <p className="text-2xl font-bold text-emerald-600">+${stats.resolved}</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="py-24 border-y border-slate-100 bg-slate-50/50">
        <div className="max-w-7xl mx-auto px-6">
          <div className="text-center max-w-2xl mx-auto mb-16 animate-fade-in-up">
            <h2 className="text-3xl md:text-4xl font-bold text-slate-900 mb-4">Everything you need</h2>
            <p className="text-slate-500">Streamlined incident submission, live tracking, and team analytics — all in one place.</p>
          </div>
          <div className="grid md:grid-cols-3 gap-6">
            ${html`<${FeatureCard} key=${1} icon=${html`<svg className="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M12 5v14M5 12h14"/></svg>`} title="Submit an incident" description="Create detailed support tickets with location, issue type, priority, and file attachments." to="/form" color="blue" />`}
            ${html`<${FeatureCard} key=${2} icon=${html`<svg className="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>`} title="Track & manage" description="Filter, update, assign, and monitor ticket status with a live dashboard and incident history." to="/dashboard" color="emerald" />`}
            ${html`<${FeatureCard} key=${3} icon=${html`<svg className="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M3 3v18h18"/><path d="M18 17V9"/><path d="M13 17V5"/><path d="M8 17v-3"/></svg>`} title="Team performance" description="Click through team members to see workload, resolution rates, and incident details." to="/dashboard" color="violet" />`}
          </div>
        </div>
      </section>

      <section className="py-20">
        <div className="max-w-7xl mx-auto px-6">
          <div className="grid md:grid-cols-3 gap-8 text-center">
            <div className="rounded-3xl border border-slate-200 bg-white/40 p-8 animate-fade-in-up">
              <p className="text-4xl font-bold text-blue-600 mb-2">24/7</p>
              <p className="text-sm text-slate-500 uppercase tracking-wider">Support coverage</p>
            </div>
            <div className="rounded-3xl border border-slate-200 bg-white/40 p-8 animate-fade-in-up animate-fade-in-up-delay-1">
              <p className="text-4xl font-bold text-indigo-400 mb-2">INC</p>
              <p className="text-sm text-slate-500 uppercase tracking-wider">ServiceNow-style IDs</p>
            </div>
            <div className="rounded-3xl border border-slate-200 bg-white/40 p-8 animate-fade-in-up animate-fade-in-up-delay-2">
              <p className="text-4xl font-bold text-violet-600 mb-2">Real-time</p>
              <p className="text-sm text-slate-500 uppercase tracking-wider">Status updates</p>
            </div>
          </div>
        </div>
      </section>

      <footer className="border-t border-slate-200 py-8 bg-slate-50">
        <div className="max-w-7xl mx-auto px-6 flex flex-col md:flex-row items-center justify-between gap-4">
          <p className="text-sm text-slate-500">© 2026 IT Support Portal. All rights reserved.</p>
          <div className="flex items-center gap-6 text-sm text-slate-500">
            <${Link} to="/form" className="hover:text-slate-600 transition">Submit</${Link}>
            <${Link} to="/dashboard" className="hover:text-slate-600 transition">Dashboard</${Link}>
          </div>
        </div>
      </footer>
    </div>
  `
}
