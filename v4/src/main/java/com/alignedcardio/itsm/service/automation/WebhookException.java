package com.alignedcardio.itsm.service.automation;

public class WebhookException extends RuntimeException {

    private final String actionOutput;

    public WebhookException(String message, String actionOutput) {
        super(message);
        this.actionOutput = actionOutput;
    }

    public String getActionOutput() {
        return actionOutput;
    }
}
