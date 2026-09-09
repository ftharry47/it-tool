package com.alignedcardio.itsm.api.team;

import java.util.List;
import java.util.UUID;

public record TeamResponse(
        UUID id,
        String name,
        String description,
        UUID createdBy,
        UUID updatedBy,
        List<TeamMemberInfo> members
) {
}
