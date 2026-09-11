package com.alignedcardio.itsm.service.notification;

/**
 * Per-channel rendering of one notification event. Produced once by
 * NotificationTemplateBuilder so all three channels stay consistent.
 *
 * emailSubject/emailBody/emailHtmlBody — full structure for email.
 * inAppSubject/inAppBody — compact, list-view friendly (stored on the row).
 * pushTitle/pushBody — short, OS-truncation-safe (~45 / ~120 chars).
 */
public final class NotificationContent {

    private final String emailSubject;
    private final String emailBody;
    private final String emailHtmlBody;
    private final String inAppSubject;
    private final String inAppBody;
    private final String pushTitle;
    private final String pushBody;

    public NotificationContent(String emailSubject, String emailBody, String emailHtmlBody,
                               String inAppSubject, String inAppBody,
                               String pushTitle, String pushBody) {
        this.emailSubject = emailSubject;
        this.emailBody = emailBody;
        this.emailHtmlBody = emailHtmlBody;
        this.inAppSubject = inAppSubject;
        this.inAppBody = inAppBody;
        this.pushTitle = pushTitle;
        this.pushBody = pushBody;
    }

    public NotificationContent(String emailSubject, String emailBody,
                               String inAppSubject, String inAppBody,
                               String pushTitle, String pushBody) {
        this(emailSubject, emailBody, null, inAppSubject, inAppBody, pushTitle, pushBody);
    }

    public String emailSubject() { return emailSubject; }
    public String emailBody() { return emailBody; }
    public String emailHtmlBody() { return emailHtmlBody; }
    public String inAppSubject() { return inAppSubject; }
    public String inAppBody() { return inAppBody; }
    public String pushTitle() { return pushTitle; }
    public String pushBody() { return pushBody; }
}
