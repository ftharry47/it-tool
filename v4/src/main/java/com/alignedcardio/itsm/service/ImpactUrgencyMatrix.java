package com.alignedcardio.itsm.service;

public final class ImpactUrgencyMatrix {

    private ImpactUrgencyMatrix() {
    }

    public static String resolve(int impact, int urgency) {
        int score = impact * urgency;
        if (score >= 16) {
            return "Critical";
        } else if (score >= 10) {
            return "High";
        } else if (score >= 5) {
            return "Medium";
        } else {
            return "Low";
        }
    }
}
