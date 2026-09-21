package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.Team;

import java.util.List;
import java.util.UUID;

/**
 * Fixed global support-tier chain (teams seeded in V38): L1 -> L2 -> L3.
 * Shared by IncidentService (manual escalation) and SlaBreachMonitorJob
 * (policy-tier reassignment) so both agree on tier ordering.
 */
public final class SupportTiers {

    public static final UUID L1_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    public static final UUID L2_ID = UUID.fromString("00000000-0000-0000-0000-000000000021");
    public static final UUID L3_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");
    public static final UUID IT_FULFILLMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    public static final List<UUID> CHAIN = List.of(L1_ID, L2_ID, L3_ID);

    private SupportTiers() {
    }

    /** Position in the escalation chain; -1 when the team isn't a tier team. */
    public static int indexOf(Team team) {
        return team == null ? -1 : CHAIN.indexOf(team.getId());
    }
}
