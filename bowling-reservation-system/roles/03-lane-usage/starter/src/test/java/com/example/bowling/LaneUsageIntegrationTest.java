package com.example.bowling;

import com.example.bowling.repository.LaneRepository;
import com.example.bowling.repository.UsageRepository;
import com.example.bowling.service.BusinessException;
import com.example.bowling.service.LaneService;
import com.example.bowling.service.UsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * These tests run only against the isolated demo H2 database.
 * Never point them at the user's Oracle account.
 */
@SpringBootTest
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LaneUsageIntegrationTest {

    @Autowired private LaneRepository laneRepository;
    @Autowired private LaneService laneService;
    @Autowired private UsageRepository usageRepository;
    @Autowired private UsageService usageService;
    @Autowired private JdbcTemplate jdbc;

    private int count(String table) {
        // Fixed trusted table names only, no user input.
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String reservationStatus(long reservationId) {
        return jdbc.queryForObject(
            "SELECT reservation_status FROM RESERVATION WHERE reservation_id=?",
            String.class, reservationId);
    }

    @Test void demoDataIsLoaded() {
        assertThat(count("MEMBER")).isEqualTo(3);
        assertThat(count("LANE")).isEqualTo(10);
        assertThat(count("RESERVATION")).isEqualTo(3);
        assertThat(count("RESERVATION_LANE")).isEqualTo(4);
        assertThat(count("USAGE")).isEqualTo(1);
        assertThat(laneRepository.findAll()).hasSize(10);
        assertThat(laneRepository.findOne(9).laneStatus()).isEqualTo("점검중");
        assertThat(laneRepository.schedules(2)).hasSize(1);
    }

    @Test void startAndFinishChangesReservationAndUsageTogether() {
        long usageId = usageService.start(1001, 1);
        assertThat(usageId).isGreaterThan(0);
        assertThat(reservationStatus(1001)).isEqualTo("이용중");
        assertThat(laneRepository.findOne(1).occupied()).isTrue();
        assertThat(count("USAGE")).isEqualTo(2);
        usageService.finish(usageId);
        assertThat(reservationStatus(1001)).isEqualTo("이용완료");
        assertThat(laneRepository.findOne(1).occupied()).isFalse();
        assertThat(jdbc.queryForObject(
            "SELECT actual_end_time FROM USAGE WHERE usage_id=?", java.sql.Timestamp.class,usageId))
            .isNotNull();
    }

    @Test void aMultipleLaneReservationFinishesOnlyAfterAllLanesAreDone() {
        long lane2Usage = usageService.start(1002, 2);
        long lane3Usage = usageService.start(1002, 3);
        usageService.finish(lane2Usage);
        assertThat(reservationStatus(1002)).isEqualTo("이용중");
        usageService.finish(lane3Usage);
        assertThat(reservationStatus(1002)).isEqualTo("이용완료");
    }

    @Test void startingAUsageTwiceDoesNotInsertDuplicateRows() {
        usageService.start(1001, 1);
        assertThatThrownBy(() -> usageService.start(1001, 1))
            .isInstanceOf(BusinessException.class);
        assertThat(count("USAGE")).isEqualTo(2);
    }

    @Test void anUnassignedLaneCannotBeStarted() {
        assertThatThrownBy(() -> usageService.start(1001, 2))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("배정된 레인");
        assertThat(count("USAGE")).isEqualTo(1);
    }

    @Test void aCompletedReservationCannotStartAgain() {
        assertThatThrownBy(() -> usageService.start(1003, 4))
            .isInstanceOf(BusinessException.class);
    }

    @Test void invalidFacilityStatusesAreRejected() {
        assertThatThrownBy(() -> laneService.updateStatus(4,"고장"))
            .isInstanceOf(BusinessException.class);
    }

    @Test void laneMaintenanceIsBlockedWhileCurrentBookingExists() {
        assertThatThrownBy(() -> laneService.updateStatus(1,"점검중"))
            .isInstanceOf(BusinessException.class);
        assertThat(laneRepository.findOne(1).laneStatus()).isEqualTo("이용가능");
    }

    @Test void aLaneWithoutActiveOrFutureBookingCanBeMarkedForMaintenance() {
        laneService.updateStatus(8,"점검중");
        assertThat(laneRepository.findOne(8).laneStatus()).isEqualTo("점검중");
    }

    @Test void aNonBookableLaneCannotStartEvenIfAReservationWasAssignedToIt() {
        // Deliberately insert an assignment that violates the application rule
        // but not the ERD's FK constraint, to verify the service rejects it.
        jdbc.update("""
            INSERT INTO RESERVATION (reservation_id,member_id,start_time,end_time,person_count,reservation_status)
            VALUES (1004,1,DATEADD('MINUTE',-10,CURRENT_TIMESTAMP()),
                DATEADD('MINUTE',80,CURRENT_TIMESTAMP()),1,'예약확정')
            """);
        jdbc.update("INSERT INTO RESERVATION_LANE (reservation_id,lane_id) VALUES (1004,9)");
        assertThatThrownBy(() -> usageService.start(1004,9))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("이용가능");
    }

    @Test void completedUsageCannotBeEndedTwice() {
        assertThatThrownBy(() -> usageService.finish(5001))
            .isInstanceOf(BusinessException.class);
    }
}
