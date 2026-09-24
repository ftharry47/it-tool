package com.alignedcardio.itsm.service.notification;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared email wrapper. Emits a full HTML document: a quiet wordmark header
 * ("Azentro" text + thin red underline — no banner fills), the caller's body,
 * and the footer. Colors are defined as inline LIGHT-mode styles (the safe
 * fallback for clients that strip <style>); a prefers-color-scheme: dark
 * media block overrides the em-* classes for clients that support it.
 * Plain-text bodies are escaped and converted to simple <p>/<br> HTML;
 * URL-only lines become clickable links.
 */
public final class EmailRenderer {

    private static final Pattern URL_LINE = Pattern.compile("^https?://[^\\s]+$|^/[^\\s]*$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_]+)\\}\\}");
    private static final String FONT =
            "-apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif";

    private EmailRenderer() {
    }

    /**
     * Wraps a fully-formed body HTML fragment in the email document.
     */
    public static String render(String bodyHtml) {
        return "<!DOCTYPE html>\n<html>\n<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<meta name=\"color-scheme\" content=\"light dark\">\n"
                + "<meta name=\"supported-color-schemes\" content=\"light dark\">\n"
                + "<style>\n"
                + "  a { color: #dc2828; text-decoration: none; }\n"
                + "  @media (prefers-color-scheme: dark) {\n"
                + "    .em-canvas { background-color: #0a0a0d !important; }\n"
                + "    .em-card { background-color: #17171c !important; border-color: #3f3f4a !important; }\n"
                + "    .em-text { color: #fafafa !important; }\n"
                + "    .em-muted { color: #9ca3af !important; }\n"
                + "    .em-quote { background-color: #1c1c22 !important; color: #d4d4d8 !important; }\n"
                + "    a, .em-link { color: #f87171 !important; }\n"
                + "  }\n"
                + "</style>\n</head>\n"
                + "<body class=\"em-canvas\" style=\"margin:0; padding:28px 16px; background-color:#f4f4f5;\">\n"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n"
                + "  <tr><td align=\"center\">\n"
                + "    <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "class=\"em-card\" style=\"max-width:600px; background-color:#ffffff; "
                + "border:1px solid #e2e2e8; border-radius:8px;\">\n"
                + "      <tr><td style=\"padding:22px 28px 4px 28px;\">\n"
                + "        <span class=\"em-text\" style=\"font-family:" + FONT
                + "; font-size:17px; font-weight:600; color:#1a1a1e;\">Azentro</span>\n"
                + "        <div style=\"width:44px; border-bottom:3px solid #dc2828; margin-top:8px;\"></div>\n"
                + "      </td></tr>\n"
                + "      <tr><td class=\"em-text\" style=\"padding:14px 28px 8px 28px; font-family:" + FONT
                + "; font-size:15px; line-height:1.6; color:#1a1a1e;\">\n"
                + bodyHtml
                + "      </td></tr>\n"
                + "      <tr><td class=\"em-card\" style=\"padding:16px 28px 20px 28px; "
                + "border-top:1px solid #e2e2e8;\">\n"
                + "        <p class=\"em-muted\" style=\"margin:0 0 8px 0; font-family:" + FONT
                + "; font-size:13px; color:#6b7280;\">"
                + "If you have questions about this ticket, reply from the portal or contact your IT team."
                + "</p>\n"
                + "        <p class=\"em-muted\" style=\"margin:0; font-family:" + FONT
                + "; font-size:12px; color:#6b7280;\">© 2026 Azentro. All Rights Reserved.</p>\n"
                + "      </td></tr>\n"
                + "    </table>\n"
                + "  </td></tr>\n"
                + "</table>\n"
                + "</body>\n</html>";
    }

    /**
     * Converts a plain-text body (with blank-line paragraph breaks) into safe
     * HTML and renders the full email.
     */
    public static String fromPlainText(String plainText) {
        return render(toHtml(plainText));
    }

    /**
     * Replaces placeholders in a template, then converts to a full HTML email.
     */
    public static String renderTemplate(String template, java.util.Map<String, ?> values, String appBaseUrl) {
        String filled = fill(template, values, appBaseUrl);
        return fromPlainText(filled);
    }

    /**
     * Fills {{key}} placeholders. Built-ins: {{appBaseUrl}}. Missing names
     * ending in "FirstName" default to "there"; other missing keys to "".
     */
    public static String fill(String template, java.util.Map<String, ?> values, String appBaseUrl) {
        if (template == null) return "";
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            Object value = values.get(key);
            if (value == null) {
                if ("appBaseUrl".equals(key)) value = appBaseUrl;
                else if (key.endsWith("FirstName")) value = "there";
                else value = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Escapes and paragraphs plain text. URL-only lines are turned into links.
     */
    public static String toHtml(String plain) {
        if (plain == null) return "";
        String[] paragraphs = plain.split("\\n\\n+");
        StringBuilder out = new StringBuilder();
        for (String para : paragraphs) {
            out.append("<p style=\"margin: 0 0 1em 0;\">");
            String[] lines = para.split("\\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                if (URL_LINE.matcher(line).matches()) {
                    String href = line.startsWith("/") ? line : line;
                    out.append("<a href=\"").append(escape(href)).append("\" class=\"em-link\" style=\"color: #dc2828; text-decoration: none;\">")
                            .append(escape(line)).append("</a>");
                } else {
                    out.append(escape(line));
                }
                if (i < lines.length - 1) out.append("<br>");
            }
            out.append("</p>");
        }
        return out.toString();
    }

    public static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Escape + preserve line structure inside an existing HTML block.
     * Plain escape() collapses \n — comment bodies quoted in emails were
     * rendered as one long run-on line. Also renders **bold** as <strong>.
     */
    public static String escapeMultiline(String text) {
        if (text == null) return "";
        return escape(text)
                .replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>")
                .replace("\n", "<br>");
    }

}
