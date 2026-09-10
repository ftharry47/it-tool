package com.alignedcardio.itsm.api.search;

import com.alignedcardio.itsm.api.change.ChangeResponse;
import com.alignedcardio.itsm.api.incident.IncidentSummary;
import com.alignedcardio.itsm.api.kb.KbSearchResult;
import com.alignedcardio.itsm.api.problem.ProblemResponse;
import com.alignedcardio.itsm.api.user.UserResponse;

import java.util.List;

public record GlobalSearchResponse(
        List<IncidentSummary> incidents,
        List<ProblemResponse> problems,
        List<ChangeResponse> changes,
        List<KbSearchResult> kb,
        List<UserResponse> users
) {
}
