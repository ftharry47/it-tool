package com.alignedcardio.itsm.service.automation;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

@Component
public class WebhookUrlValidator {

    private static final List<String> BLOCKED_SCHEMES = List.of("file", "ftp", "smtp", "ldap");

    public void validate(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new WebhookException("Invalid webhook URL: " + url, url);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            throw new WebhookException("Webhook URL must be http or https: " + url, url);
        }

        String host = uri.getHost();
        if (host == null) {
            throw new WebhookException("Webhook URL must include a host: " + url, url);
        }

        if (host.equalsIgnoreCase("localhost") || host.endsWith(".local") || host.endsWith(".localhost")) {
            throw new WebhookException("Webhook URL points to a blocked local host: " + host, url);
        }

        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress()) {
                throw new WebhookException("Webhook URL resolves to loopback: " + host, url);
            }
            if (address.isSiteLocalAddress()) {
                throw new WebhookException("Webhook URL resolves to a private IP: " + host, url);
            }
            if (address.isLinkLocalAddress()) {
                throw new WebhookException("Webhook URL resolves to a link-local IP: " + host, url);
            }
        } catch (UnknownHostException e) {
            throw new WebhookException("Webhook URL host could not be resolved: " + host, url);
        }
    }
}
