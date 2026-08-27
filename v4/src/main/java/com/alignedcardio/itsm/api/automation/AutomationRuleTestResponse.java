package com.alignedcardio.itsm.api.automation;

import com.fasterxml.jackson.databind.JsonNode;

public record AutomationRuleTestResponse(
        boolean matched,
        JsonNode actions
) {
}
