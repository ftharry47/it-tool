package com.alignedcardio.itsm.api.problem;

import com.alignedcardio.itsm.entity.Problem;

public record ProblemUpdateRequest(
        String title,
        String description,
        Problem.Status status,
        String rootCause,
        String workaround,
        java.util.UUID assigneeId
) {
}
