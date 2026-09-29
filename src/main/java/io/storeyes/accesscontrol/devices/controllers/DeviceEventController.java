package io.storeyes.accesscontrol.devices.controllers;

import io.storeyes.accesscontrol.devices.config.DeviceIngestProperties;
import io.storeyes.accesscontrol.devices.dto.HikvisionEvent;
import io.storeyes.accesscontrol.devices.parsing.HikvisionEventParser;
import io.storeyes.accesscontrol.devices.services.DeviceEventService;
import io.storeyes.accesscontrol.logs.dto.NotificationBatch;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receives Hikvision access-control event pushes (HTTP host notification / "listening mode").
 *
 * <p>Point a terminal's {@code /ISAPI/Event/notification/httpHosts} entry at
 * {@code http://<this-host>:<port>/api/device-events}. The device sends {@code multipart/form-data}
 * with a JSON {@code event_log} part (and, for face/fingerprint events, a JPEG we ignore); some
 * firmware sends a bare {@code application/json} or {@code application/xml} body instead — both are
 * handled, mirroring {@code Storeyes-Projects/hikvision-webhooks/main.py:141-193}.
 *
 * <p>Spring's eager multipart resolver is disabled ({@code spring.servlet.multipart.enabled=false})
 * because terminal firmware routinely half-closes the socket or overstates {@code Content-Length},
 * which made {@code StandardServletMultipartResolver} reject the whole request with a 400 before this
 * handler ran. Instead we read the body ourselves, tolerate a truncated stream, and parse the
 * multipart framing by hand.
 *
 * <p>The endpoint always answers {@code 200 {"status":...}} once a request is understood, so the
 * device does not enter its re-push loop; deduplication happens in {@link DeviceEventService}.
 * The terminal itself sends no {@code X-STORE-CODE}: behind the st-app-back proxy it is set from the
 * terminal's registered store; a direct push falls back to the configured {@code device.ingest.tenant}.
 *
 * <p>A processed event answers {@code {"status":"ok","notifications":{...}}} when the punch produced a
 * notification batch (e.g. a LATE check-in); the proxy dispatches it and the terminal ignores it.
 */
@RestController
@RequestMapping("/api/device-events")
@RequiredArgsConstructor
public class DeviceEventController {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventController.class);

    /** Cap the in-memory body copy; a face snapshot is well under this. */
    private static final int MAX_BODY_BYTES = 15 * 1024 * 1024;

    private static final byte[] HEADER_SEPARATOR = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

    private final HikvisionEventParser parser;
    private final DeviceEventService deviceEventService;
    private final DeviceIngestProperties properties;

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "listening");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> receive(HttpServletRequest request) throws IOException {
        if (!properties.enabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "disabled"));
        }
        if (properties.requiresSecret() && !secretMatches(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "unauthorized"));
        }

        logInboundPush(request);

        byte[] eventPart;
        try {
            eventPart = extractEventPart(request);
        } catch (IOException e) {
            // The terminal dropped the connection before we could read the body. Nothing to process;
            // ack anyway so it doesn't spin in a re-push loop against us.
            log.warn("Device push body unreadable ({}); acknowledging", e.toString());
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }
        if (eventPart == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "reason", "no event data"));
        }

        try {
            Optional<HikvisionEvent> event = parser.parse(eventPart);
            if (event.isEmpty()) {
                // Unparseable or filtered noise — acknowledge so the device does not retry.
                return ResponseEntity.ok(Map.of("status", "ignored"));
            }
            Optional<NotificationBatch> notifications = deviceEventService.ingest(event.get());
            return ResponseEntity.ok(notifications
                    .<Map<String, Object>>map(n -> Map.of("status", "ok", "notifications", n))
                    .orElseGet(() -> Map.of("status", "ok")));
        } catch (RuntimeException e) {
            // Never let a processing failure become a non-2xx: the device would re-push indefinitely.
            log.error("Failed to process device push; acknowledging anyway to stop the re-push loop", e);
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }
    }

    /** One line per push describing what the terminal actually sent, before we touch the body. */
    private void logInboundPush(HttpServletRequest request) {
        log.info("Device push from {} ({}): {} {} | Content-Type={} Content-Length={} Transfer-Encoding={} Expect={}",
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getMethod(),
                request.getRequestURI(),
                request.getContentType(),
                request.getHeader("Content-Length"),
                request.getHeader("Transfer-Encoding"),
                request.getHeader("Expect"));
    }

    private boolean secretMatches(HttpServletRequest request) {
        String presented = StringUtils.hasText(request.getHeader("X-Device-Token"))
                ? request.getHeader("X-Device-Token")
                : request.getParameter("token");
        return properties.sharedSecret().equals(presented);
    }

    /**
     * The event payload: the multipart {@code event_log} form field, else the first form field, else
     * the first non-image file part, else the raw request body. Follows {@code main.py:148-184}.
     */
    private byte[] extractEventPart(HttpServletRequest request) throws IOException {
        if (request instanceof MultipartHttpServletRequest multipart) {
            return fromResolvedMultipart(multipart);
        }

        String contentType = request.getContentType();
        byte[] body = readBodyDefensively(request);
        if (body.length == 0) {
            return null;
        }

        if (contentType != null && contentType.regionMatches(true, 0, "multipart/", 0, "multipart/".length())) {
            String boundary = boundary(contentType);
            if (boundary == null) {
                log.warn("Multipart device push had no boundary in Content-Type: {}", contentType);
                return null;
            }
            return fromRawMultipart(body, boundary);
        }

        return body;
    }

    /** Path taken by MockMvc and any container that still hands us a resolved multipart request. */
    private byte[] fromResolvedMultipart(MultipartHttpServletRequest multipart) throws IOException {
        String field = multipart.getParameter("event_log");
        if (field == null) {
            Iterator<String> names = multipart.getParameterNames().asIterator();
            if (names.hasNext()) field = multipart.getParameter(names.next());
        }
        if (field != null) return field.getBytes(StandardCharsets.UTF_8);

        for (MultipartFile file : multipart.getFileMap().values()) {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                return file.getBytes();
            }
        }
        log.warn("Multipart device push carried no event part; params={} files={}",
                multipart.getParameterMap().keySet(), multipart.getFileMap().keySet());
        return null;
    }

    /** Read the whole body, keeping whatever arrived if the terminal drops the connection mid-stream. */
    private byte[] readBodyDefensively(HttpServletRequest request) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try (InputStream in = request.getInputStream()) {
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > MAX_BODY_BYTES) {
                    log.warn("Device push body exceeded {} bytes; parsing the leading slice", MAX_BODY_BYTES);
                    break;
                }
            }
        } catch (IOException e) {
            if (buffer.size() == 0) throw e;
            log.warn("Device push stream truncated after {} bytes ({}); parsing what arrived",
                    buffer.size(), e.toString());
        }
        log.info("Device push body read: {} bytes", buffer.size());
        return buffer.toByteArray();
    }

    /**
     * Minimal {@code multipart/form-data} split: pick the {@code event_log} part, else the first
     * non-file field, else the first non-image file. Tolerates a missing closing boundary.
     */
    private byte[] fromRawMultipart(byte[] body, String boundary) {
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        List<byte[]> fields = new ArrayList<>();
        byte[] fallbackFile = null;

        int from = indexOf(body, delimiter, 0);
        while (from >= 0) {
            int cursor = from + delimiter.length;
            if (cursor + 2 <= body.length && body[cursor] == '-' && body[cursor + 1] == '-') {
                break; // closing delimiter "--boundary--"
            }
            if (cursor + 2 <= body.length && body[cursor] == '\r' && body[cursor + 1] == '\n') {
                cursor += 2; // CRLF after the delimiter line
            }

            int next = indexOf(body, delimiter, cursor);
            int contentEnd = next >= 0 ? next : body.length;
            if (next >= 0 && contentEnd - cursor >= 2
                    && body[contentEnd - 2] == '\r' && body[contentEnd - 1] == '\n') {
                contentEnd -= 2; // CRLF that belongs to the following delimiter
            }

            int headerEnd = indexOf(body, HEADER_SEPARATOR, cursor);
            if (headerEnd >= 0 && headerEnd < contentEnd) {
                String headers = new String(body, cursor, headerEnd - cursor, StandardCharsets.ISO_8859_1);
                int valueStart = Math.min(headerEnd + HEADER_SEPARATOR.length, contentEnd);
                byte[] value = Arrays.copyOfRange(body, valueStart, contentEnd);

                String name = headerParam(headers, "name");
                String filename = headerParam(headers, "filename");
                String partType = headerValue(headers, "content-type");

                if ("event_log".equals(name)) {
                    return value;
                }
                if (filename == null) {
                    fields.add(value);
                } else if (fallbackFile == null
                        && (partType == null || !partType.toLowerCase(Locale.ROOT).startsWith("image/"))) {
                    fallbackFile = value;
                }
            }

            from = next;
        }

        if (!fields.isEmpty()) return fields.get(0);
        if (fallbackFile != null) return fallbackFile;
        log.warn("Raw multipart device push carried no event part (boundary={})", boundary);
        return null;
    }

    private static String boundary(String contentType) {
        for (String segment : contentType.split(";")) {
            String trimmed = segment.trim();
            if (trimmed.regionMatches(true, 0, "boundary=", 0, "boundary=".length())) {
                String value = trimmed.substring("boundary=".length()).trim();
                if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private static String headerParam(String headers, String param) {
        Matcher matcher = Pattern.compile(Pattern.quote(param) + "\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE)
                .matcher(headers);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String headerValue(String headers, String headerName) {
        for (String line : headers.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(headerName)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(from, 0); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
