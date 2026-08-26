import React, { useState, useEffect } from 'https://cdn.jsdelivr.net/npm/react@18.3.1/+esm'
import htm from 'https://cdn.jsdelivr.net/npm/htm@3.1.1/+esm'
import { callAPI } from '../api.js'
import { StatusBadge, PriorityBadge } from '../components/Badge.js'

const html = htm.bind(React.createElement)
const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    if (file.size > 5 * 1024 * 1024) { reject(new Error('Max 5 MB per image')); return }
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

function formatDate(d) {
  try { return new Date(d).toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }) } catch { return d }
}

export default function FormPage() {
  const [tab, setTab] = useState('submit')
  const [config, setConfig] = useState(null)
  const [form, setForm] = useState({ name: '', email: '', phone: '', location: '', shortDescription: '', issueType: '', impactArea: '', criticalFlag: false })
  const [attachments, setAttachments] = useState([])
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [trackId, setTrackId] = useState('')
  const [track, setTrack] = useState(null)
  const [trackLoading, setTrackLoading] = useState(false)

  useEffect(() => {
    callAPI('getDashboardConfig').then(r => { if (r.success) setConfig(r.config) })
  }, [])

  const setField = (k, v) => setForm(f => ({ ...f, [k]: v }))

  const handleFiles = async (files) => {
    const arr = []
    for (const file of Array.from(files)) {
      if (!file.type.startsWith('image/')) continue
      try { arr.push(await fileToBase64(file)) } catch (e) { console.error(e) }
    }
    setAttachments(prev => [...prev, ...arr])
  }

  const removeFile = (i) => setAttachments(prev => prev.filter((_, idx) => idx !== i))

  const validate = () => {
    const e = {}
    if (!form.email || !emailRegex.test(form.email)) e.email = 'Enter a valid email'
    if (!form.phone || !/\d/.test(form.phone)) e.phone = 'Enter a valid phone number'
    if (!form.location) e.location = 'Select a location'
    if (!form.shortDescription || form.shortDescription.length > 500) e.shortDescription = 'Required, max 500 characters'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!validate()) return
    setLoading(true)
    try {
      const data = { ...form, attachments, priority: form.criticalFlag ? 'Critical' : 'Pending' }
      const r = await callAPI('submitTicket', data)
      setResult(r)
      if (r.success) {
        setForm({ name: '', email: '', phone: '', location: '', shortDescription: '', issueType: '', impactArea: '', criticalFlag: false })
        setAttachments([])
      }
    } catch (err) { setResult({ success: false, error: err.message }) }
    setLoading(false)
  }

  const handleTrack = async (e) => {
    e.preventDefault()
    if (!trackId.trim()) return
    setTrackLoading(true)
    try {
      const r = await callAPI('getTicketByIdForTracking', trackId.trim())
      setTrack(r)
    } catch (err) { setTrack({ success: false, error: err.message }) }
    setTrackLoading(false)
  }

  const inputClass = (err) => `w-full rounded-xl border bg-white px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 ${err ? 'border-red-500' : 'border-slate-200'}`
  const labelClass = 'mb-1.5 block text-xs font-medium text-slate-500'

  return html`
    <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col">
      <header className="border-b border-slate-200 bg-white/80 backdrop-blur-xl sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center text-lg font-bold shadow-lg shadow-blue-900/20">A</div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight">IT Support Portal</h1>
              <p className="text-xs text-slate-500">Aligned Cardio</p>
            </div>
          </div>
          <a href="/dashboard" className="text-sm font-medium text-slate-500 hover:text-slate-900 transition">Dashboard</a>
        </div>
      </header>

      <main className="flex-1 p-4 sm:p-6">
        <div className="max-w-3xl mx-auto">
          <div className="flex gap-1 mb-6 sm:mb-8 p-1 bg-white rounded-2xl border border-slate-200">
            <button onClick=${() => setTab('submit')} className="flex-1 py-2.5 rounded-xl text-sm font-semibold transition ${tab === 'submit' ? 'bg-blue-600 text-white shadow-md' : 'text-slate-500 hover:text-slate-900'}">Submit Incident</button>
            <button onClick=${() => setTab('track')} className="flex-1 py-2.5 rounded-xl text-sm font-semibold transition ${tab === 'track' ? 'bg-blue-600 text-white shadow-md' : 'text-slate-500 hover:text-slate-900'}">Track Incident</button>
          </div>

          ${tab === 'submit' && html`
            <div className="rounded-3xl border border-slate-200 bg-white backdrop-blur-xl p-6 sm:p-8 shadow-xl">
              <h2 className="text-2xl font-bold mb-2">Submit a new incident</h2>
              <p className="text-sm text-slate-500 mb-6">Provide the details below. Do not include patient names or MRNs.</p>

              <form onSubmit=${handleSubmit} className="space-y-5">
                <div className="grid sm:grid-cols-2 gap-5">
                  <div>
                    <label className=${labelClass}>Full Name <span className="text-slate-500">(optional)</span></label>
                    <input type="text" value=${form.name} onChange=${e => setField('name', e.target.value)} placeholder="John Doe" className=${inputClass(errors.name)} />
                  </div>
                  <div>
                    <label className=${labelClass}>Email <span className="text-red-600">*</span></label>
                    <input type="email" value=${form.email} onChange=${e => setField('email', e.target.value)} placeholder="you@company.com" className=${inputClass(errors.email)} />
                    ${errors.email && html`<p className="mt-1.5 text-xs text-red-600">${errors.email}</p>`}
                  </div>
                </div>

                <div className="grid sm:grid-cols-2 gap-5">
                  <div>
                    <label className=${labelClass}>Phone <span className="text-red-600">*</span></label>
                    <input type="tel" value=${form.phone} onChange=${e => setField('phone', e.target.value)} placeholder="(123) 456-7890" className=${inputClass(errors.phone)} />
                    ${errors.phone && html`<p className="mt-1.5 text-xs text-red-600">${errors.phone}</p>`}
                  </div>
                  <div>
                    <label className=${labelClass}>Location <span className="text-red-600">*</span></label>
                    <select value=${form.location} onChange=${e => setField('location', e.target.value)} className=${inputClass(errors.location)}>
                      <option value="">Select a location</option>
                      ${(config?.locations || []).map(loc => html`<option value=${loc} key=${loc}>${loc}</option>`)}
                    </select>
                    ${errors.location && html`<p className="mt-1.5 text-xs text-red-600">${errors.location}</p>`}
                  </div>
                </div>

                <div>
                  <label className=${labelClass}>Issue Type</label>
                  <select value=${form.issueType} onChange=${e => setField('issueType', e.target.value)} className=${inputClass()}>
                    <option value="">Select an issue type</option>
                    ${(config?.issueTypes || []).map(t => html`<option value=${t} key=${t}>${t}</option>`)}
                  </select>
                </div>

                <div>
                  <label className=${labelClass}>Impact Area</label>
                  <select value=${form.impactArea} onChange=${e => setField('impactArea', e.target.value)} className=${inputClass()}>
                    <option value="">Select an impact area</option>
                    ${(config?.impactAreas || []).map(a => html`<option value=${a} key=${a}>${a}</option>`)}
                  </select>
                </div>

                <div>
                  <label className=${labelClass}>Short Description <span className="text-red-600">*</span></label>
                  <textarea value=${form.shortDescription} onChange=${e => setField('shortDescription', e.target.value)} maxLength="500" rows="4" placeholder="Describe the IT issue briefly" className=${inputClass(errors.shortDescription)}></textarea>
                  <div className="mt-1.5 flex justify-between text-xs text-slate-500">
                    <span>${errors.shortDescription || ''}</span>
                    <span>${form.shortDescription.length}/500</span>
                  </div>
                </div>

                <div className="flex items-center gap-3 p-3 rounded-xl border border-red-200 bg-red-50 cursor-pointer" onClick=${() => setField('criticalFlag', !form.criticalFlag)}>
                  <input type="checkbox" checked=${form.criticalFlag} onChange=${e => setField('criticalFlag', e.target.checked)} className="w-5 h-5 rounded border-slate-300 bg-white text-red-500 focus:ring-red-500" />
                  <div>
                    <p className="text-sm font-medium text-red-600">This is a critical / patient-care impacting issue</p>
                    <p className="text-xs text-slate-500">Mark only if the issue affects clinical operations.</p>
                  </div>
                </div>

                <div>
                  <label className=${labelClass}>Attachments</label>
                  <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-4 hover:border-blue-500/50 transition">
                    <input type="file" id="fileInput" accept="image/*" multiple onChange=${e => handleFiles(e.target.files)} className="hidden" />
                    <label htmlFor="fileInput" className="cursor-pointer flex flex-col items-center gap-2 text-sm text-slate-500 hover:text-slate-800">
                      <span className="px-3 py-1.5 rounded-lg bg-slate-100 text-slate-800 text-xs font-medium">Choose images</span>
                      <span>PNG, JPG up to 5 MB each</span>
                    </label>
                    <div className="flex flex-wrap gap-3 mt-4">
                      ${attachments.map((a, i) => html`
                        <div key=${i} className="relative w-20 h-20 rounded-lg overflow-hidden border border-slate-200 group">
                          <img src=${a} className="w-full h-full object-cover" alt="attachment" />
                          <button type="button" onClick=${() => removeFile(i)} className="absolute inset-0 bg-black/60 text-white text-xs opacity-0 group-hover:opacity-100 transition flex items-center justify-center">Remove</button>
                        </div>
                      `)}
                    </div>
                  </div>
                </div>

                <button type="submit" disabled=${loading} className="w-full py-3.5 rounded-xl bg-blue-600 text-white hover:bg-blue-700 font-semibold shadow-md hover:shadow-blue-500/20 transition disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2">
                  ${loading && html`<div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>`}
                  <span>${loading ? 'Submitting...' : 'Submit Incident'}</span>
                </button>
              </form>
            </div>
          `}

          ${tab === 'track' && html`
            <div className="rounded-3xl border border-slate-200 bg-white backdrop-blur-xl p-6 sm:p-8 shadow-xl">
              <h2 className="text-2xl font-bold mb-2">Track your incident</h2>
              <p className="text-sm text-slate-500 mb-6">Enter the incident number you received, e.g. INC0000001.</p>

              <form onSubmit=${handleTrack} className="flex gap-3 mb-8">
                <input type="text" value=${trackId} onChange=${e => setTrackId(e.target.value)} placeholder="INC0000001" className=${inputClass()} />
                <button type="submit" disabled=${trackLoading} className="px-6 py-3 rounded-xl bg-blue-600 text-white font-semibold hover:bg-blue-500 transition disabled:opacity-60">${trackLoading ? 'Searching...' : 'Search'}</button>
              </form>

              ${track && !track.success && html`<div className="p-4 rounded-xl border border-red-200 bg-red-50 text-red-600 text-sm">${track.error}</div>`}

              ${track?.success && html`
                <div className="rounded-2xl border border-slate-200 bg-white p-5 space-y-4">
                  <div className="flex flex-wrap items-center justify-between gap-3 pb-4 border-b border-slate-200">
                    <div>
                      <p className="text-xs text-slate-500">Incident Number</p>
                      <p className="text-lg font-bold font-mono text-blue-600">${track.ticket?.['Ticket ID']}</p>
                    </div>
                    <div className="flex gap-2">
                      <${StatusBadge} status=${track.ticket?.Status} />
                      <${PriorityBadge} priority=${track.ticket?.Priority} />
                    </div>
                  </div>

                  <div className="grid sm:grid-cols-2 gap-4 text-sm">
                    <div><span className="text-slate-500 block text-xs">Created</span><span className="text-slate-800">${formatDate(track.ticket?.['Created Date'])}</span></div>
                    <div><span className="text-slate-500 block text-xs">Location</span><span className="text-slate-800">${track.ticket?.Location}</span></div>
                    <div><span className="text-slate-500 block text-xs">Issue Type</span><span className="text-slate-800">${track.ticket?.['Issue Type']}</span></div>
                    <div><span className="text-slate-500 block text-xs">Assigned To</span><span className="text-slate-800">${track.ticket?.['Assigned To'] || 'Unassigned'}</span></div>
                  </div>

                  <div>
                    <span className="text-slate-500 text-xs">Description</span>
                    <p className="mt-1 text-sm text-slate-800">${track.ticket?.['Short Description']}</p>
                  </div>

                  ${track.history?.length > 0 && html`
                    <div>
                      <span className="text-slate-500 text-xs">Activity Timeline</span>
                      <div className="mt-3 space-y-3">
                        ${track.history.map((h, i) => html`
                          <div key=${i} className="flex gap-3 text-sm">
                            <div className="w-2 h-2 mt-1.5 rounded-full bg-blue-500"></div>
                            <div>
                              <p className="text-slate-800 font-medium">${h.action} <span className="text-slate-500 text-xs">${formatDate(h.timestamp)}</span></p>
                              <p className="text-slate-500 text-xs">${h.notes}</p>
                            </div>
                          </div>
                        `)}
                      </div>
                    </div>
                  `}
                </div>
              `}
            </div>
          `}
        </div>
      </main>

      ${result && html`
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm" onClick=${() => setResult(null)}>
          <div className="w-full max-w-md rounded-3xl border border-slate-200 bg-white p-6 shadow-xl" onClick=${e => e.stopPropagation()}>
            ${result.success ? html`
              <div className="text-center">
                <div className="w-14 h-14 mx-auto rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center mb-4 text-emerald-600 text-2xl">✓</div>
                <h3 className="text-xl font-bold mb-1">Incident Submitted</h3>
                <p className="text-sm text-slate-500 mb-4">Your incident number is</p>
                <p className="text-2xl font-mono font-bold text-blue-600 mb-6">${result.ticketId}</p>
                <div className="flex gap-3 justify-center">
                  <button onClick=${() => { setResult(null); setTab('track'); setTrackId(result.ticketId); }} className="px-5 py-2.5 rounded-xl bg-blue-600 text-white text-sm font-semibold hover:bg-blue-500">Track</button>
                  <button onClick=${() => setResult(null)} className="px-5 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50">Close</button>
                </div>
              </div>
            ` : html`
              <div className="text-center">
                <div className="w-14 h-14 mx-auto rounded-full bg-red-50 border border-red-200 flex items-center justify-center mb-4 text-red-600 text-2xl">!</div>
                <h3 className="text-xl font-bold mb-1">Submission Failed</h3>
                <p className="text-sm text-red-600 mb-6">${result.error}</p>
                <button onClick=${() => setResult(null)} className="px-5 py-2.5 rounded-xl bg-slate-100 text-white text-sm font-semibold hover:bg-white/15">Try Again</button>
              </div>
            `}
          </div>
        </div>
      `}
    </div>
  `
}


