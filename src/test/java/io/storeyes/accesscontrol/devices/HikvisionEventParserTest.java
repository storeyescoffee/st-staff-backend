package io.storeyes.accesscontrol.devices;

import io.storeyes.accesscontrol.devices.dto.HikvisionEvent;
import io.storeyes.accesscontrol.devices.parsing.HikvisionEventParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Parsing + noise filter for Hikvision access-control event pushes. */
class HikvisionEventParserTest {

    private final HikvisionEventParser parser = new HikvisionEventParser(new ObjectMapper());

    private Optional<HikvisionEvent> parse(String payload) {
        return parser.parse(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String event(int major, int sub, String employeeNo) {
        String person = employeeNo == null ? "" : "\"employeeNoString\":\"" + employeeNo + "\",";
        return """
                {"eventType":"AccessControllerEvent","dateTime":"2026-09-02T08:05:00+01:00",
                 "macAddress":"aa:bb:cc:dd:ee:ff",
                 "AccessControllerEvent":{"majorEventType":%d,"subEventType":%d,%s
                   "serialNo":1001,"currentVerifyMode":"face","name":"Alice"}}
                """.formatted(major, sub, person);
    }

    @Test
    void extractsAVerificationSuccessEvent() {
        HikvisionEvent e = parse(event(5, 38, "E001")).orElseThrow();

        assertThat(e.employeeCode()).isEqualTo("E001");
        assertThat(e.eventType()).isEqualTo("AccessControllerEvent");
        assertThat(e.dateTime().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(e.dateTime().toLocalTime()).isEqualTo(LocalTime.of(8, 5));
        assertThat(e.majorEventType()).isEqualTo(5);
        assertThat(e.subEventType()).isEqualTo(38);
        assertThat(e.serialNo()).isEqualTo(1001L);
        assertThat(e.verifyMode()).isEqualTo("face");
        assertThat(e.personName()).isEqualTo("Alice");
        assertThat(e.sourceKey()).isEqualTo("aa:bb:cc:dd:ee:ff:1001");
    }

    @Test
    void dropsDoorStatusChanges() {
        assertThat(parse(event(3, 21, "E001"))).isEmpty();
        assertThat(parse(event(3, 22, "E001"))).isEmpty();
    }

    @Test
    void dropsSystemAndNetworkChatter() {
        assertThat(parse(event(2, 1, null))).isEmpty();
        assertThat(parse(event(3, 1, null))).isEmpty();
    }

    @Test
    void dropsRemoteAdminLoginAndReaderAttempts() {
        assertThat(parse(event(5, 122, "admin"))).isEmpty();
        assertThat(parse(event(5, 39, "E001"))).isEmpty();
    }

    @Test
    void dropsEventsWithNoPersonContext() {
        assertThat(parse(event(5, 38, null))).isEmpty();
    }

    @Test
    void dropsUnparseablePayloads() {
        assertThat(parser.parse("not json or xml".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(parser.parse(new byte[0])).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
    }

    @Test
    void fallsBackToXmlForOlderFirmware() {
        String xml = """
                <EventNotificationAlert>
                  <eventType>AccessControllerEvent</eventType>
                  <dateTime>2026-09-02T17:30:00+01:00</dateTime>
                  <AccessControllerEvent>
                    <majorEventType>5</majorEventType>
                    <subEventType>38</subEventType>
                    <employeeNoString>E042</employeeNoString>
                    <serialNo>77</serialNo>
                  </AccessControllerEvent>
                </EventNotificationAlert>
                """;

        HikvisionEvent e = parse(xml).orElseThrow();
        assertThat(e.employeeCode()).isEqualTo("E042");
        assertThat(e.dateTime().toLocalTime()).isEqualTo(LocalTime.of(17, 30));
        assertThat(e.serialNo()).isEqualTo(77L);
    }

    @Test
    void acceptsADateTimeWithNoOffset() {
        String json = """
                {"eventType":"AccessControllerEvent","dateTime":"2026-09-02T09:15:00",
                 "AccessControllerEvent":{"majorEventType":5,"subEventType":38,
                   "employeeNoString":"E7","serialNo":5}}
                """;
        HikvisionEvent e = parse(json).orElseThrow();
        assertThat(e.dateTime().toLocalTime()).isEqualTo(LocalTime.of(9, 15));
    }

    @Test
    void derivesADeterministicSourceKeyWhenNoSerialIsSent() {
        String json = """
                {"eventType":"AccessControllerEvent","dateTime":"2026-09-02T08:05:00+01:00",
                 "ipAddress":"10.0.0.5",
                 "AccessControllerEvent":{"majorEventType":5,"subEventType":38,"employeeNoString":"E9"}}
                """;
        String first = parse(json).orElseThrow().sourceKey();
        String second = parse(json).orElseThrow().sourceKey();
        assertThat(first).isEqualTo(second).contains("10.0.0.5").contains("E9");
    }
}
