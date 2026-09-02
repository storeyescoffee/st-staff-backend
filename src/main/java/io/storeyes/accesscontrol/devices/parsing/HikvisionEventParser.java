package io.storeyes.accesscontrol.devices.parsing;

import io.storeyes.accesscontrol.devices.dto.HikvisionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import tools.jackson.databind.ObjectMapper;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Parses a Hikvision access-control event push into a normalized {@link HikvisionEvent}, or drops it.
 *
 * <p>Reimplements the reference receiver at {@code Storeyes-Projects/hikvision-webhooks/main.py}:
 * the event part is JSON on current firmware ({@code main.py:70-72}) with an XML fallback for older
 * firmware ({@code main.py:14-18}), and the noise filter ({@code main.py:102-118}) discards door
 * status, system/network chatter, admin logins and reader heartbeats. Anything without an
 * {@code employeeNoString} is dropped too — we can only turn a person's swipe into a punch.
 */
@Component
public class HikvisionEventParser {

    private static final Logger log = LoggerFactory.getLogger(HikvisionEventParser.class);

    private final ObjectMapper objectMapper;

    public HikvisionEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param eventPart raw bytes of the event part (multipart {@code event_log} field or a bare body)
     * @return the normalized event, or empty when the payload is unparseable or filtered as noise
     */
    public Optional<HikvisionEvent> parse(byte[] eventPart) {
        if (eventPart == null || eventPart.length == 0) return Optional.empty();

        Map<String, Object> root = readJson(eventPart).orElseGet(() -> readXml(eventPart).orElse(null));
        if (root == null) {
            log.warn("Device event payload was neither JSON nor XML: {} bytes", eventPart.length);
            return Optional.empty();
        }

        Map<String, Object> ace = asMap(root.get("AccessControllerEvent"));
        Integer major = asInt(ace.get("majorEventType"));
        Integer sub = asInt(ace.get("subEventType"));
        String employeeCode = trimToNull(asString(ace.get("employeeNoString")));

        if (isNoise(major, sub, employeeCode)) {
            log.debug("Filtered device noise event: major={} sub={} employee={}", major, sub, employeeCode);
            return Optional.empty();
        }

        OffsetDateTime dateTime = parseDateTime(asString(root.get("dateTime")));
        if (dateTime == null) {
            log.warn("Device event for employee {} had no usable dateTime; dropping", employeeCode);
            return Optional.empty();
        }

        Long serialNo = asLong(ace.get("serialNo"));
        String sourceKey = buildSourceKey(root, ace, serialNo, dateTime, employeeCode, sub);

        return Optional.of(new HikvisionEvent(
                asString(root.get("eventType")),
                dateTime,
                employeeCode,
                major,
                sub,
                serialNo,
                trimToNull(asString(ace.get("currentVerifyMode"))),
                trimToNull(asString(ace.get("name"))),
                sourceKey));
    }

    /** Port of {@code main.py:102-118} plus: no person context means nothing to punch. */
    private boolean isNoise(Integer major, Integer sub, String employeeCode) {
        boolean hasPerson = employeeCode != null;
        if (!hasPerson) return true; // system/network chatter, door status, reader heartbeat, ...

        boolean doorStatus = sub != null && (sub == 21 || sub == 22);
        boolean adminLogin = sub != null && sub == 122;
        boolean readerAttempt = sub != null && sub == 39; // failed / attempted verification — not a punch
        return doorStatus || adminLogin || readerAttempt;
    }

    private String buildSourceKey(Map<String, Object> root, Map<String, Object> ace, Long serialNo,
                                  OffsetDateTime dateTime, String employeeCode, Integer sub) {
        String device = firstNonBlank(
                asString(root.get("macAddress")),
                asString(ace.get("macAddress")),
                asString(root.get("ipAddress")),
                asString(ace.get("deviceName")),
                "dev");
        String key = serialNo != null
                ? device + ":" + serialNo
                : device + ":" + dateTime + ":" + employeeCode + ":" + sub;
        return key.length() > 200 ? key.substring(0, 200) : key;
    }

    // ---------- payload decoding ----------

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> readJson(byte[] bytes) {
        try {
            Object parsed = objectMapper.readValue(bytes, Map.class);
            return parsed instanceof Map<?, ?> m ? Optional.of((Map<String, Object>) m) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<Map<String, Object>> readXml(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(bytes));
            return Optional.of(flatten(doc.getDocumentElement()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Mirrors {@code xml_to_dict} in the reference: nested elements become nested maps, leaves become text. */
    private Map<String, Object> flatten(Element element) {
        Map<String, Object> result = new LinkedHashMap<>();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element child = (Element) node;
            String tag = stripNamespace(child.getTagName());
            result.put(tag, hasElementChildren(child) ? flatten(child) : child.getTextContent().trim());
        }
        return result;
    }

    private boolean hasElementChildren(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) return true;
        }
        return false;
    }

    private String stripNamespace(String tag) {
        int colon = tag.indexOf(':');
        return colon >= 0 ? tag.substring(colon + 1) : tag;
    }

    // ---------- scalar coercion ----------

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private OffsetDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "dev";
    }
}
