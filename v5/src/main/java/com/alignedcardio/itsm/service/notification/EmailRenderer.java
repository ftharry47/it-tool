package com.alignedcardio.itsm.service.notification;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared email wrapper. Renders a table-based text wordmark header and the
 * exact closing signature around the caller's body HTML. Plain-text bodies
 * are escaped and converted to simple <p>/<br> HTML; URL-only lines become
 * clickable links.
 */
public final class EmailRenderer {

    private static final Pattern URL_LINE = Pattern.compile("^https?://[^\\s]+$|^/[^\\s]*$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_]+)\\}\\}");

    private EmailRenderer() {
    }

    /**
     * Wraps a fully-formed body HTML fragment with the header and footer.
     */
    public static String render(String bodyHtml) {
        return header() + "<div style=\"font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif; font-size: 15px; line-height: 1.5; color: #222;\">"
                + bodyHtml
                + "</div>" + footer();
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
                    out.append("<a href=\"").append(escape(href)).append("\" style=\"color: #2563eb; text-decoration: none;\">")
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

    private static String header() {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"background: #0f172a; border-radius: 6px 6px 0 0; margin-bottom: 20px;\">\n"
                + "  <tr>\n"
                + "    <td style=\"padding: 20px;\">\n"
                + "      <span style=\"color: #ffffff; font-size: 18px; font-weight: 600;\">"
                + "Azentro | One Mail Delivery System"
                + "</span>\n"
                + "    </td>\n"
                + "  </tr>\n"
                + "</table>\n";
    }

    private static String footer() {
        return "<p style=\"margin-top: 24px; color: #555;\">"
                + "If you have questions about this ticket, reply from the portal or contact your IT team."
                + "</p>\n"
                + "<p style=\"color: #555;\">Regards,<br>Azentro | One Mail Delivery System</p>\n";
    }
}
