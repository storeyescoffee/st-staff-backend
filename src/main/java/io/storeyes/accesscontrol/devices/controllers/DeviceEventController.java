package io.storeyes.accesscontrol.devices.controllers;

import io.storeyes.accesscontrol.devices.config.DeviceIngestProperties;
import io.storeyes.accesscontrol.devices.dto.HikvisionEvent;
import io.storeyes.accesscontrol.devices.parsing.HikvisionEventParser;
import io.storeyes.accesscontrol.devices.services.DeviceEventService;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Receives Hikvision access-control event pushes (HTTP host notification / "listening mode").
 *
 * <p>Point a terminal's {@code /ISAPI/Event/notification/httpHosts} entry at
 * {@code http://<this-host>:<port>/api/device-events}. The device sends {@code multipart/form-data}
 * with a JSON {@code event_log} part (and, for face/fingerprint events, a JPEG we ignore); some
 * firmware sends a bare {@code application/json} or {@code application/xml} body instead — both are
 * handled, mirroring {@code Storeyes-Projects/hikvision-webhooks/main.py:141-193}.
 *
 * <p>The endpoint always answers {@code 200 {"status":...}} once a request is understood, so the
 * device does not enter its re-push loop; deduplication happens in {@link DeviceEventService}.
 * There is no {@code X-STORE-CODE} on a device push — {@code TenantFilter} attributes these requests
 * to the configured {@code device.ingest.tenant}.
 */
@RestController
@RequestMapping("/api/device-events")
@RequiredArgsConstructor
public class DeviceEventController {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventController.class);

    private final HikvisionEventParser parser;
    private final DeviceEventService deviceEventService;
    private final DeviceIngestProperties properties;

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "listening");
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> receive(HttpServletRequest request) throws IOException {
        if (!properties.enabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "disabled"));
        }
        if (properties.requiresSecret() && !secretMatches(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "unauthorized"));
        }

        byte[] eventPart = extractEventPart(request);
        if (eventPart == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "reason", "no event data"));
        }

        Optional<HikvisionEvent> event = parser.parse(eventPart);
        if (event.isEmpty()) {
            // Unparseable or filtered noise — acknowledge so the device does not retry.
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }

        deviceEventService.ingest(event.get());
        return ResponseEntity.ok(Map.of("status", "ok"));
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
            String field = multipart.getParameter("event_log");
            if (field == null) {
                Iterator<String> names = multipart.getParameterNames().asIterator();
                if (names.hasNext()) field = multipart.getParameter(names.next());
            }
            if (field != null) return field.getBytes(StandardCharsets.UTF_8);

            for (MultipartFile file : multipart.getFileMap().values()) {
                String contentType = file.getContentType();
                if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
                    return file.getBytes();
                }
            }
            log.warn("Multipart device push carried no event part; params={} files={}",
                    multipart.getParameterMap().keySet(), multipart.getFileMap().keySet());
            return null;
        }

        byte[] body = request.getInputStream().readAllBytes();
        return body.length == 0 ? null : body;
    }
}
