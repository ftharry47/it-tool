export function HowItWorks() {
  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-4xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">How This Works</h1>
        <p className="text-muted-foreground">
          A reference for new Super Admins covering the end-to-end processes in the portal.
        </p>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">Roles & Visibility</h2>
          <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            <li><strong>END_USER</strong> — creates incidents and service requests, sees only their own, can comment on any ticket they can view.</li>
            <li><strong>AGENT / TEAM_LEAD</strong> — org-wide list visibility, can transition assigned incidents, can comment on any incident or service request.</li>
            <li><strong>ADMIN / SUPER_ADMIN</strong> — full admin access, user/location/catalog/workflow configuration, and all reports.</li>
          </ul>
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
          <h2 className="mb-3 text-lg font-semibold">Service Request Lifecycle</h2>
          <ol className="list-decimal space-y-1 pl-5 text-sm text-muted-foreground">
            <li><strong>SUBMITTED</strong> — request logged by the requester.</li>
            <li><strong>PENDING_APPROVAL</strong> — routed to an approver if the catalog item requires it.</li>
            <li><strong>APPROVED</strong> — moves into fulfillment.</li>
            <li><strong>IN_FULFILLMENT</strong> — tasks progress through PENDING → ORDERED → DELIVERY_DATE_SET → DELIVERED → COMPLETED.</li>
            <li><strong>FULFILLED</strong> — all tasks complete, request closed.</li>
            <li>Rejected requests move to <strong>REJECTED</strong>; retroactive send-to-approval is ADMIN/SUPER_ADMIN only and preserves fulfillment state.</li>
          </ol>
          <p className="mt-2 text-sm text-muted-foreground">
            Fulfillment workflows can be FULL (physical items with order/delivery/install), SOFTWARE (provision/grant), or INSTANT (single completion step).
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
            <li>SLA policies define response and resolution targets per business hours calendar.</li>
            <li>SLA clocks pause in ON_HOLD, WAITING_ON_CUSTOMER, and PENDING_APPROVAL.</li>
            <li>Escalation tiers fire on response breach, resolution breach, or stuck status and can reassign to a higher tier (L1 → L2 → L3).</li>
            <li>Soft-deleted tickets are excluded from current SLA and reporting calculations.</li>
          </ul>
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">Support Tiers & Teams</h2>
          <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            <li><strong>L1 Support</strong> — first-line triage and basic resolution.</li>
            <li><strong>L2 Support</strong> — deeper technical work and incident escalations.</li>
            <li><strong>L3 Support</strong> — specialist/architect ownership and complex problems.</li>
            <li>Teams can be assigned manually or through tier escalation policies.</li>
          </ul>
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">Reports & Saved Queries</h2>
          <p className="text-sm text-muted-foreground">
            The Reports section includes Tickets Summary, SLA Compliance, Agent Workload, Sprint Velocity, Tickets by Location, and a configurable ad-hoc query builder. Soft-deleted records are excluded from current/live calculations, while saved/historical snapshots remain unchanged.
          </p>
        </section>
      </div>
    </div>
  )
}
