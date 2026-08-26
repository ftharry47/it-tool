import React, { useState, useEffect } from 'https://cdn.jsdelivr.net/npm/react@18.3.1/+esm'
import htm from 'https://cdn.jsdelivr.net/npm/htm@3.1.1/+esm'
import { callAPI } from '../api.js'
import { StatusBadge, PriorityBadge } from '../components/Badge.js'

const html = htm.bind(React.createElement)
const noteTypes = ['General', 'Internal', 'Customer Update']

function formatDate(d) {
  try { return new Date(d).toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }) } catch { return d }
}

export default function TicketModal({ ticket, onClose, onUpdate, user, staff, statuses, priorities, config }) {
  const [details, setDetails] = useState(null)
  const [newStatus, setNewStatus] = useState(ticket?.['Status'] || 'Open')
  const [newPriority, setNewPriority] = useState(ticket?.['Priority'] || 'Pending')
  const [newAssignee, setNewAssignee] = useState(ticket?.['Assigned To'] || '')
  const [note, setNote] = useState('')
  const [noteType, setNoteType] = useState('General')
  const [escalateTo, setEscalateTo] = useState('')
  const [escalationLevel, setEscalationLevel] = useState('L2')
  const [activeTab, setActiveTab] = useState('details')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!ticket) return
    setNewStatus(ticket['Status'] || 'Open')
    setNewPriority(ticket['Priority'] || 'Pending')
    setNewAssignee(ticket['Assigned To'] || '')
    setDetails(null)
    setMessage('')
    callAPI('getTicketByIdForTracking', ticket['Ticket ID']).then(r => { if (r.success) setDetails(r) })
  }, [ticket])

  const run = async (fn, ...args) => {
    setBusy(true)
    try {
      const r = await callAPI(fn, ...args)
      setMessage(r.success ? r.message : (r.error || 'Failed'))
      if (r.success) onUpdate()
    } catch (e) { setMessage(e.message) }
    setBusy(false)
  }

  const handleStatus = (e) => { e.preventDefault(); run('updateTicketStatus', ticket['Ticket ID'], newStatus, user.displayName) }
  const handlePriority = (e) => { e.preventDefault(); run('updateTicketPriority', ticket['Ticket ID'], newPriority, user.displayName) }
  const handleAssign = (e) => { e.preventDefault(); run('assignTicket', ticket['Ticket ID'], newAssignee, user.displayName, newPriority) }
  const handleNote = (e) => { e.preventDefault(); if (!note.trim()) return; run('addTicketNote', ticket['Ticket ID'], note, user.displayName, noteType); setNote('') }
  const handleEscalate = (e) => { e.preventDefault(); if (!escalateTo) return; run('escalateTicket', ticket['Ticket ID'], escalateTo, escalationLevel, user.displayName, '') }

  const inputClass = 'w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20'
  const btnClass = 'px-4 py-2 rounded-xl text-sm font-semibold bg-blue-600 text-white hover:bg-blue-500 transition disabled:opacity-50'
  const tabClass = (t) => `px-3 py-2 text-sm font-medium rounded-lg transition ${activeTab === t ? 'bg-blue-600 text-white' : 'text-slate-500 hover:text-slate-900 hover:bg-slate-50'}`
  const isViewer = user.role === 'Viewer'

  return html`
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-slate-900/50 backdrop-blur-sm" onClick=${onClose}></div>
      <div className="relative w-full max-w-3xl max-h-[90vh] overflow-y-auto rounded-3xl border border-slate-200 bg-white shadow-xl">
        <div className="sticky top-0 z-10 flex items-center justify-between p-5 border-b border-slate-200 bg-white/95 backdrop-blur">
          <div className="flex items-center gap-3">
            <div>
              <p className="text-xs text-slate-500">Incident Number</p>
              <p className="text-lg font-mono font-bold text-blue-600">${ticket['Ticket ID']}</p>
            </div>
            <${StatusBadge} status=${ticket['Status']} />
            <${PriorityBadge} priority=${ticket['Priority']} />
          </div>
          <button onClick=${onClose} className="w-8 h-8 rounded-full bg-slate-100 text-slate-500 hover:text-slate-900 flex items-center justify-center">×</button>
        </div>

        <div className="p-5">
          <div className="flex gap-2 mb-5 overflow-x-auto">
            <button onClick=${() => setActiveTab('details')} className=${tabClass('details')}>Details</button>
            <button onClick=${() => setActiveTab('activity')} className=${tabClass('activity')}>Activity</button>
            <button onClick=${() => setActiveTab('notes')} className=${tabClass('notes')}>Notes</button>
            ${!isViewer && html`<button onClick=${() => setActiveTab('actions')} className=${tabClass('actions')}>Actions</button>`}
          </div>

          ${message && html`<div className="mb-4 p-3 rounded-xl border border-blue-200 bg-blue-50 text-sm text-blue-600">${message}</div>`}

          ${activeTab === 'details' && html`
            <div className="grid sm:grid-cols-2 gap-4 text-sm mb-4">
              <div><span className="text-slate-500 text-xs block">Requester</span><span className="text-slate-800">${ticket['Name']}</span></div>
              <div><span className="text-slate-500 text-xs block">Email</span><span className="text-slate-800">${ticket['Email Address']}</span></div>
              <div><span className="text-slate-500 text-xs block">Phone</span><span className="text-slate-800">${ticket['Phone Number']}</span></div>
              <div><span className="text-slate-500 text-xs block">Location</span><span className="text-slate-800">${ticket['Location']}</span></div>
              <div><span className="text-slate-500 text-xs block">Issue Type</span><span className="text-slate-800">${ticket['Issue Type']}</span></div>
              <div><span className="text-slate-500 text-xs block">Impact Area</span><span className="text-slate-800">${ticket['Impact Area']}</span></div>
              <div><span className="text-slate-500 text-xs block">Created</span><span className="text-slate-800">${formatDate(ticket['Created Date'])}</span></div>
              <div><span className="text-slate-500 text-xs block">Assigned To</span><span className="text-slate-800">${ticket['Assigned To'] || 'Unassigned'}</span></div>
            </div>
            <div className="space-y-4 text-sm">
              <div>
                <span className="text-slate-500 text-xs block mb-1">Short Description</span>
                <p className="text-slate-800 p-3 rounded-xl bg-slate-50 border border-slate-100">${ticket['Short Description']}</p>
              </div>
              ${ticket['Additional Description'] && html`
                <div>
                  <span className="text-slate-500 text-xs block mb-1">Additional Description</span>
                  <p className="text-slate-600 p-3 rounded-xl bg-slate-50 border border-slate-100">${ticket['Additional Description']}</p>
                </div>
              `}
            </div>
          `}

          ${activeTab === 'activity' && html`
            <div className="space-y-3">
              ${details?.history?.length ? details.history.map((h, i) => html`
                <div key=${i} className="flex gap-3 text-sm">
                  <div className="w-2 h-2 mt-1.5 rounded-full bg-blue-500"></div>
                  <div>
                    <p className="text-slate-800 font-medium">${h.action} <span className="text-slate-500 text-xs">${formatDate(h.timestamp)}</span></p>
                    <p className="text-slate-500 text-xs">${h.notes}</p>
                  </div>
                </div>
              `) : html`<p className="text-sm text-slate-500">No activity recorded.</p>`}
            </div>
          `}

          ${activeTab === 'notes' && html`
            <div className="space-y-4">
              ${!isViewer && html`
                <form onSubmit=${handleNote} className="flex flex-col gap-3 p-4 rounded-2xl border border-slate-200 bg-slate-50">
                  <textarea value=${note} onChange=${e => setNote(e.target.value)} rows="3" placeholder="Add a note..." className=${inputClass}></textarea>
                  <div className="flex gap-3">
                    <select value=${noteType} onChange=${e => setNoteType(e.target.value)} className=${inputClass}>
                      ${noteTypes.map(t => html`<option value=${t} key=${t}>${t}</option>`)}
                    </select>
                    <button type="submit" disabled=${busy || !note.trim()} className=${btnClass}>Add Note</button>
                  </div>
                </form>
              `}
              <div className="space-y-3">
                ${details?.notes?.length ? details.notes.map((n, i) => html`
                  <div key=${i} className="p-3 rounded-xl border border-slate-200 bg-slate-50">
                    <div className="flex justify-between items-center mb-1">
                      <span className="text-xs font-medium text-blue-600">${n.noteType}</span>
                      <span className="text-xs text-slate-500">${formatDate(n.timestamp)}</span>
                    </div>
                    <p className="text-sm text-slate-800">${n.noteText}</p>
                    <p className="text-xs text-slate-500 mt-1">by ${n.addedBy}</p>
                  </div>
                `) : html`<p className="text-sm text-slate-500">No notes yet.</p>`}
              </div>
            </div>
          `}

          ${activeTab === 'actions' && !isViewer && html`
            <div className="space-y-5">
              <form onSubmit=${handleStatus} className="p-4 rounded-2xl border border-slate-200 bg-slate-50 space-y-3">
                <p className="text-sm font-medium text-slate-800">Update Status</p>
                <div className="flex gap-3">
                  <select value=${newStatus} onChange=${e => setNewStatus(e.target.value)} className=${inputClass}>
                    ${statuses.map(s => html`<option value=${s} key=${s}>${s}</option>`)}
                  </select>
                  <button type="submit" disabled=${busy} className=${btnClass}>Update</button>
                </div>
              </form>

              <form onSubmit=${handlePriority} className="p-4 rounded-2xl border border-slate-200 bg-slate-50 space-y-3">
                <p className="text-sm font-medium text-slate-800">Update Priority</p>
                <div className="flex gap-3">
                  <select value=${newPriority} onChange=${e => setNewPriority(e.target.value)} className=${inputClass}>
                    ${priorities.map(p => html`<option value=${p} key=${p}>${p}</option>`)}
                  </select>
                  <button type="submit" disabled=${busy} className=${btnClass}>Update</button>
                </div>
              </form>

              <form onSubmit=${handleAssign} className="p-4 rounded-2xl border border-slate-200 bg-slate-50 space-y-3">
                <p className="text-sm font-medium text-slate-800">Assign / Reassign</p>
                <div className="flex gap-3">
                  <select value=${newAssignee} onChange=${e => setNewAssignee(e.target.value)} className=${inputClass}>
                    <option value="">Select staff</option>
                    ${(staff || []).map(s => html`<option value=${s.name} key=${s.name}>${s.name} (${s.level})</option>`)}
                  </select>
                  <button type="submit" disabled=${busy || !newAssignee} className=${btnClass}>Assign</button>
                </div>
              </form>

              <form onSubmit=${handleEscalate} className="p-4 rounded-2xl border border-slate-200 bg-slate-50 space-y-3">
                <p className="text-sm font-medium text-slate-800">Escalate</p>
                <div className="grid sm:grid-cols-3 gap-3">
                  <select value=${escalateTo} onChange=${e => setEscalateTo(e.target.value)} className=${inputClass}>
                    <option value="">Select staff</option>
                    ${(staff || []).filter(s => s.level !== 'L1' && s.level !== 'Admin').map(s => html`<option value=${s.name} key=${s.name}>${s.name} (${s.level})</option>`)}
                  </select>
                  <select value=${escalationLevel} onChange=${e => setEscalationLevel(e.target.value)} className=${inputClass}>
                    <option value="L2">L2</option>
                    <option value="L3">L3</option>
                  </select>
                  <button type="submit" disabled=${busy || !escalateTo} className=${btnClass}>Escalate</button>
                </div>
              </form>
            </div>
          `}
        </div>
      </div>
    </div>
  `
}


