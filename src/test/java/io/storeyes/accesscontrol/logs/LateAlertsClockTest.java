package io.storeyes.accesscontrol.logs;

import io.storeyes.accesscontrol.logs.controllers.EmployeeLogController;
import io.storeyes.accesscontrol.logs.services.EmployeeLogService;
import io.storeyes.accesscontrol.logs.services.LateAlertService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * The late-alert window is evaluated on the server clock (UTC), the clock the shifts' planned times are
 * expressed in, not on a local zone such as Africa/Casablanca (UTC+1), which fired alerts an hour early.
 */
class LateAlertsClockTest {

    private TimeZone original;

    @BeforeEach
    void runInUtc() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC")); // as AccesscontrolApplication does
    }

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
    }

    @Test
    void lateAlertsAreClaimedAtTheServerClock() {
        LateAlertService lateAlertService = Mockito.mock(LateAlertService.class);
        EmployeeLogController controller =
                new EmployeeLogController(Mockito.mock(EmployeeLogService.class), lateAlertService);

        LocalDateTime before = LocalDateTime.now();
        controller.lateAlerts();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(lateAlertService).claimDueAlerts(now.capture());
        assertThat(now.getValue()).isBetween(before, after.plus(Duration.ofSeconds(1)));
    }
}
