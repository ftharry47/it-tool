import { ArrowLeft } from 'lucide-react'
import { useSmartBack } from '../../lib/useSmartBack'

export function HowItWorks() {
  const smartBack = useSmartBack('/admin')
  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-4xl space-y-6">
        <button
          onClick={smartBack}
          className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ArrowLeft className="h-4 w-4" />
          Back
        </button>
        <h1 className="text-2xl font-semibold tracking-tight">How This Works</h1>
        <p className="text-muted-foreground">
          A reference for new Super Admins covering the end-to-end processes in the portal.
        </p>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">Roles & Visibility</h2>
          <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            <li><strong>END_USER</strong> — creates incidents and service requests, sees only their own, can comment on any ticket they can view, and can cancel their own open requests.</li>
            <li><strong>AGENT / TEAM_LEAD</strong> — org-wide list visibility, can transition assigned incidents, progress their assigned fulfillment tasks, hold/resume requests, and comment on any incident or service request.</li>
            <li><strong>ADMIN</strong> — full admin access: users, locations, catalog, workflows, and all reports/exports.</li>
            <li><strong>SUPER_ADMIN</strong> — everything above plus record-level overrides: editing a service request's submitted data, bypassing pending approvals, assigning/reassigning fulfillment tasks, SLA reset, and reopening resolved incidents.</li>
          </ul>
          <p className="mt-2 text-sm text-muted-foreground">
            The SLA section on Incident and Service Request detail pages is visible to staff only — end users never see SLA clocks.
          </p>
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">Incident Lifecycle</h2>
          <ol className="list-decimal space-y-1 pl-5 text-sm text-muted-foreground">
            <li><strong>NEW</strong> — reported and unassigned.</li>
            <li><strong>IN_PROGRESS</strong> — assigned agent is working it.</li>
            <li><strong>ON_HOLD / WAITING_ON_CUSTOMER</strong> — paused for external input (SLA pauses here).</li>
            <li><strong>RESOLVED</strong> — work complete, solution recorded.</li>
            <li><strong>CLOSED</strong> — final state after review or customer confirmation.</li>
            <li><strong>REOPENED</strong> — ADMIN/SUPER_ADMIN only; can move back into the workflow.</li>
          </ol>
          <p className="mt-2 text-sm text-muted-foreground">
            Status transitions are restricted to the assigned agent (pre-tier-escalation) or ADMIN/SUPER_ADMIN. Tier escalation (L1 → L2 → L3) clears the assignee and freezes the original agent out of transitions.
          </p>
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">Service Request Lifecycle & Actions</h2>
          <ol className="list-decimal space-y-1 pl-5 text-sm text-muted-foreground">
            <li><strong>SUBMITTED</strong> — request logged by the requester.</li>
            <li><strong>PENDING_APPROVAL</strong> — routed to an approver if the catalog item requires it; the approver can Approve or Reject (with reason). SUPER_ADMIN can Bypass Approval (with reason, audited) or Edit the request — changing its location re-routes approval to the new location's manager.</li>
            <li><strong>APPROVED / IN_FULFILLMENT</strong> — fulfillment tasks progress PENDING → ORDERED → DELIVERY_DATE_SET → DELIVERED → COMPLETED (for SOFTWARE items the steps read Provisioned/Granted; INSTANT items complete in a single step).</li>
            <li><strong>ON_HOLD</strong> — staff can place a request on hold (confirmation required): the SLA clock pauses and task progression is frozen — Ordered, Delivery Date, Installed, and Complete are all blocked until Resume.</li>
            <li><strong>FULFILLED</strong> — all tasks complete, request closed.</li>
            <li><strong>REJECTED / REJECTED_NEEDS_REVIEW</strong> — an approver's refusal; needs-review can be re-approved.</li>
            <li><strong>CANCELLED</strong> — the request is withdrawn or no longer needed (distinct from rejection). Available from any non-terminal status to the requester (their own) or staff; a reason is always required and is recorded in the audit trail. The approver and assigned fulfillers are notified.</li>
          </ol>
          <p className="mt-2 text-sm text-muted-foreground">
            Fulfillment task assignment is SUPER_ADMIN-only, restricted to IT Fulfillment team members — the action is labelled "Assign" on unassigned tasks and "Reassign" on assigned ones; both use the same mechanism. The request's priority is unaffected by assignment. Retroactive send-to-approval is ADMIN/SUPER_ADMIN only and preserves fulfillment state.
          </p>
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">Problem & Change Management</h2>
          <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            <li><strong>Problems</strong> group related incidents, track root cause and known-error status.</li>
            <li><strong>Changes</strong> require CAB approval by type/risk; the change calendar shows scheduled windows.</li>
          </ul>
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">SLA & Escalation Tiers</h2>
          <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            <li>SLA policies define response and resolution targets counted in business minutes on a business hours calendar, and apply to Incidents, Requests, Problems, or Changes. An optional priority filter scopes which tickets a policy governs — for changes it matches the risk level (LOW/MEDIUM/HIGH); problems carry no priority filter.</li>
            <li><strong>Response met</strong> when the ticket is first worked on: an incident leaves NEW, a problem enters INVESTIGATING, a change is APPROVED, a request starts fulfillment.</li>
            <li><strong>Resolution met</strong> at a terminal state: RESOLVED/CLOSED for incidents and problems, COMPLETED/CLOSED for changes (FAILED/ROLLED_BACK/CANCELLED/REJECTED also stop the clock), FULFILLED or CANCELLED for requests. Cancelled requests are excluded from SLA compliance calculations entirely — a withdrawal is neither met nor breached.</li>
            <li>Incident/request SLA clocks pause in ON_HOLD, WAITING_ON_CUSTOMER, and PENDING_APPROVAL; paused minutes are added back to due times on resume.</li>
            <li>Breach status moves ON_TRACK → AT_RISK at 75% of the resolution clock → BREACHED past the due time. AT_RISK notifies the assignee; BREACHED notifies the requester (or the assignee when there is no requester).</li>
            <li>Escalation tiers fire on response breach, resolution breach, or stuck status. Every tier notifies its configured role plus ADMIN/SUPER_ADMIN; on incidents a tier can also reassign the ticket to a higher support tier (L1 → L2 → L3). If the ticket is already at the target tier — or higher — the reassignment is suppressed and the audit records it as "Auto-escalation suppressed" with a SAME_TIER or DOWNGRADE reason, never a false same-to-same move. Problems/changes/requests escalate by notification only.</li>
            <li>Creating, updating, or deleting a policy notifies the agents currently holding open tickets under it and members of teams referenced by its escalation tiers.</li>
            <li>Soft-deleted tickets are excluded from all SLA lists and reporting calculations; agents always see only their own SLA rows, admins see the org-wide view plus per-team/per-agent compliance breakdowns.</li>
            <li><strong>Reset SLA</strong> (SUPER_ADMIN, under Admin → SLA dashboard) wipes all SLA instances and recreates fresh clocks anchored at the reset moment — reporting history starts over from that point, so compliance returns to 100% until new breaches occur.</li>
          </ul>
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">Support Tiers & Teams</h2>
          <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            <li><strong>L1 Support</strong> — first-line triage and basic resolution.</li>
            <li><strong>L2 Support</strong> — deeper technical work and incident escalations.</li>
            <li><strong>L3 Support</strong> — specialist/architect ownership and complex problems.</li>
            <li><strong>IT Fulfillment</strong> — the team whose members can be assigned service-request fulfillment tasks (SUPER_ADMIN assigns).</li>
            <li>Teams can be assigned manually or through tier escalation policies.</li>
          </ul>
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">Reports & Data Export</h2>
          <p className="text-sm text-muted-foreground">
            Reporting is consolidated into three places:
          </p>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            <li><strong>Reports</strong> — pre-built views: Tickets Summary, SLA Compliance, Agent Workload, Tickets I Worked On, Sprint Velocity, plus Incidents by Category, Requests by Catalog, SLA by Priority, and Approval Backlog. Every chart slice, bar, metric card, and table row drills into the real filtered ticket list.</li>
            <li><strong>Tickets I Worked On</strong> — audit-driven: shows tickets the agent took an action on (status change, assignment, escalation, task work) within the date range — regardless of when the ticket was created.</li>
            <li><strong>Query Builder</strong> — Grouped mode counts tickets by a field; Detailed mode returns the actual matching rows with links, 50 per page. Clicking a grouped result drills into the detailed rows behind it. Filters accept names or ids (e.g. priority "Critical"), and the date range can target Created/Resolved/Closed/Decided dates. Includes starter templates (open-by-agent, SLA breaches by location, pending approvals &gt;3 days, and more) plus canned SLA compliance-by-agent/location reports — service-request SLA is attributed to the fulfiller.</li>
            <li><strong>Data Export</strong> (ADMIN/SUPER_ADMIN) — row-level CSV/XLSX pulls of Incidents, Service Requests, Problems, Changes, or SLA Instances, filtered by date range and status.</li>
          </ul>
          <p className="mt-2 text-sm text-muted-foreground">
            Soft-deleted records are excluded from current/live calculations, while saved/historical snapshots remain unchanged.
          </p>
        </section>
      </div>
    </div>
  )
}
