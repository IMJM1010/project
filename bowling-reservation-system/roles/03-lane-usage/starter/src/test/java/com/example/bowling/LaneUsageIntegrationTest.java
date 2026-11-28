package com.example.bowling;
import com.example.bowling.repository.*;
import com.example.bowling.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import java.time.*;
import java.sql.Timestamp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.security.test.context.support.WithMockUser;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Import(LaneUsageIntegrationTest.FixedClock.class) @ActiveProfiles("demo") @WithMockUser(roles="ADMIN")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LaneUsageIntegrationTest {
 @Autowired LaneRepository lanes;@Autowired UsageRepository repo;@Autowired UsageService usage;@Autowired LaneService service;@Autowired JdbcTemplate jdbc;
 static final LocalDateTime NOW=LocalDateTime.of(2026,11,28,13,15);
 @TestConfiguration static class FixedClock {
  @Bean @Primary Clock testClock(){return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(),ZoneId.of("Asia/Seoul"));}
 }
 @BeforeEach void alignFixtures(){
  jdbc.update("UPDATE RESERVATION SET reservation_start_time=?,reservation_end_time=? WHERE reservation_id IN (1001,1002)",Timestamp.valueOf(NOW.minusMinutes(15)),Timestamp.valueOf(NOW.plusMinutes(45)));
  jdbc.update("UPDATE RESERVATION_LANE SET assigned_start_time=? WHERE reservation_id IN (1001,1002)",Timestamp.valueOf(NOW.minusMinutes(15)));
  jdbc.update("UPDATE RESERVATION SET reservation_start_time=?,reservation_end_time=? WHERE reservation_id=1003",Timestamp.valueOf(NOW.minusDays(1)),Timestamp.valueOf(NOW.minusDays(1).plusHours(1)));
  jdbc.update("UPDATE RESERVATION_LANE SET assigned_start_time=? WHERE reservation_id=1003",Timestamp.valueOf(NOW.minusDays(1)));
  jdbc.update("UPDATE USAGE SET actual_start_time=?,actual_end_time=? WHERE usage_id=5001",Timestamp.valueOf(NOW.minusDays(1)),Timestamp.valueOf(NOW.minusDays(1).plusMinutes(55)));
 }
 private void booking(long id,long member,LocalDateTime start,LocalDateTime end,long lane){
  jdbc.update("INSERT INTO RESERVATION(reservation_id,member_id,reservation_start_time,reservation_end_time,person_count,status) VALUES(?,?,?,?,1,'CONFIRMED')",id,member,Timestamp.valueOf(start),Timestamp.valueOf(end));
  jdbc.update("INSERT INTO RESERVATION_LANE(reservation_id,lane_id,assigned_start_time) VALUES(?,?,?)",id,lane,Timestamp.valueOf(start));
 }
 private String status(long id){return repo.booking(id).status().name();}
 @Test void initial(){assertThat(lanes.findAll()).hasSize(10);assertThat(lanes.findOne(9).laneStatus()).isEqualTo("MAINTENANCE");}
 @Test void startEnd(){long id=usage.startUsage(101);assertThat(status(1001)).isEqualTo("IN_USE");assertThat(lanes.findOne(1).occupied()).isTrue();usage.endUsage(id);assertThat(status(1001)).isEqualTo("COMPLETED");assertThat(lanes.findOne(1).laneStatus()).isEqualTo("AVAILABLE");}
 @Test void multi(){long a=usage.startUsage(102);long b=usage.startUsage(103);usage.endUsage(a);assertThat(status(1002)).isEqualTo("IN_USE");usage.endUsage(b);assertThat(status(1002)).isEqualTo("COMPLETED");}
 @Test void duplicate(){usage.startUsage(101);assertThatThrownBy(()->usage.startUsage(101)).isInstanceOf(BusinessException.class);assertThat(repo.byAssignment(101)).hasSize(1);}
 @Test void unused(){long a=usage.startUsage(102);usage.markUnused(103);usage.endUsage(a);assertThat(status(1002)).isEqualTo("COMPLETED");assertThat(repo.byAssignment(103)).isEmpty();}
 @Test void allUnusedRollback(){assertThatThrownBy(()->usage.markUnused(101)).isInstanceOf(BusinessException.class);assertThat(repo.assignment(101).status().name()).isEqualTo("WAITING");}
 @Test void completedCannotRestart(){assertThatThrownBy(()->usage.startUsage(104)).isInstanceOf(BusinessException.class);}
 @Test void completedCannotEndAgain(){assertThatThrownBy(()->usage.endUsage(5001)).isInstanceOf(BusinessException.class);}
 @Test void maintenanceAndRestore(){service.markMaintenance(8);assertThat(lanes.findOne(8).laneStatus()).isEqualTo("MAINTENANCE");service.restoreLane(8);assertThat(lanes.findOne(8).laneStatus()).isEqualTo("AVAILABLE");}
 @Test void reallocate(){var result=service.markMaintenance(1);assertThat(result.replaced()).isEqualTo(1);assertThat(repo.assignment(101).laneId()).isNotEqualTo(1);}
 @Test void activeFailureEndsUsage(){long id=usage.startUsage(101);service.markMaintenance(1);assertThat(repo.usage(id).end()).isNotNull();assertThat(lanes.findOne(1).laneStatus()).isEqualTo("MAINTENANCE");assertThat(repo.assignment(101).laneId()).isEqualTo(1);}
 @Test void wholeFinish(){usage.startUsage(102);usage.finishReservation(1002);assertThat(status(1002)).isEqualTo("COMPLETED");assertThat(repo.assignment(103).status().name()).isEqualTo("UNUSED");}
 @Test void capacity(){usage.recordAttendance(1001,5);assertThatThrownBy(()->usage.startUsage(101)).isInstanceOf(BusinessException.class);assertThat(repo.byAssignment(101)).isEmpty();}
 @Test void addLane(){usage.startUsage(101);usage.recordAttendance(1001,5);long id=usage.addLane(1001,5);assertThat(repo.usage(id).end()).isNull();assertThat(repo.assignments(1001)).hasSize(2);}
 @Test void additionalBeforeStart(){usage.recordAttendance(1001,5);long a=usage.assignAdditionalLane(1001,5);assertThat(repo.byAssignment(a)).isEmpty();usage.startUsage(101);usage.startUsage(a);assertThat(repo.assignments(1001)).hasSize(2);}
 @Test void noShowCannotStart(){jdbc.update("UPDATE RESERVATION SET status='NO_SHOW' WHERE reservation_id=1001");assertThatThrownBy(()->usage.startUsage(101)).isInstanceOf(BusinessException.class);}
 @Test void failureKeepsAssignment(){jdbc.update("UPDATE LANE SET status='MAINTENANCE' WHERE lane_id<>1");var r=service.markMaintenance(1);assertThat(r.failedReservations()).contains(1001L);assertThat(status(1001)).isEqualTo("REALLOCATION_FAILED");assertThat(repo.assignment(101).laneId()).isEqualTo(1);}
 @Test void startsAtThirteenAndExtends(){usage.startUsage(101);usage.extendReservation(1001,30);assertThat(repo.booking(1001).end()).isEqualTo(NOW.plusMinutes(75));}
 @Test void memberOverlapBlocksExtension(){usage.startUsage(101);booking(2001,101,NOW.plusMinutes(45),NOW.plusMinutes(105),6);assertThatThrownBy(()->usage.extendReservation(1001,30)).isInstanceOf(BusinessException.class);assertThat(repo.booking(1001).end()).isEqualTo(NOW.plusMinutes(45));}
 @Test void oneLaneConflictBlocksWholeExtension(){usage.startUsage(102);booking(2002,103,NOW.plusMinutes(45),NOW.plusMinutes(105),3);assertThatThrownBy(()->usage.extendReservation(1002,30)).isInstanceOf(BusinessException.class);assertThat(repo.booking(1002).end()).isEqualTo(NOW.plusMinutes(45));}
 @Test void afterMidnightBlocked(){jdbc.update("UPDATE RESERVATION SET reservation_end_time=? WHERE reservation_id=1001",Timestamp.valueOf(NOW.toLocalDate().atTime(23,30)));usage.startUsage(101);assertThatThrownBy(()->usage.extendReservation(1001,60)).isInstanceOf(BusinessException.class);}
 @Test void partialFinishThenStartRemaining(){long first=usage.startUsage(102);usage.endUsage(first);usage.startUsage(103);assertThat(status(1002)).isEqualTo("IN_USE");}
 @Test void earlyCompletionKeepsSlot(){long id=usage.startUsage(101);usage.endUsage(id);assertThat(repo.conflict(1,NOW,NOW.plusMinutes(1),9999)).isTrue();assertThat(repo.conflict(1,NOW.plusMinutes(45),NOW.plusMinutes(60),9999)).isFalse();}
 @Test void unusedAllocationKeepsSlot(){usage.startUsage(102);usage.markUnused(103);assertThat(repo.conflict(3,NOW,NOW.plusMinutes(1),9999)).isTrue();}
 @Test void addedLaneStartsAtCurrentTime(){usage.startUsage(101);usage.addLane(1001,5);assertThat(repo.assignment(1001,5).start()).isEqualTo(NOW);}
}
