package com.example.bowling;
import com.example.bowling.repository.*;
import com.example.bowling.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.security.test.context.support.WithMockUser;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("demo") @WithMockUser(roles="ADMIN")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LaneUsageIntegrationTest {
 @Autowired LaneRepository lanes;@Autowired UsageRepository repo;@Autowired UsageService usage;@Autowired LaneService service;@Autowired JdbcTemplate jdbc;
 private String status(long id){return repo.booking(id).status().name();}
 @Test void initial(){assertThat(lanes.findAll()).hasSize(10);assertThat(lanes.findOne(9).laneStatus()).isEqualTo("MAINTENANCE");}
 @Test void startEnd(){long id=usage.start(101);assertThat(status(1001)).isEqualTo("IN_USE");assertThat(lanes.findOne(1).occupied()).isTrue();usage.finish(id);assertThat(status(1001)).isEqualTo("COMPLETED");assertThat(lanes.findOne(1).laneStatus()).isEqualTo("AVAILABLE");}
 @Test void multi(){long a=usage.start(102);long b=usage.start(103);usage.finish(a);assertThat(status(1002)).isEqualTo("IN_USE");usage.finish(b);assertThat(status(1002)).isEqualTo("COMPLETED");}
 @Test void duplicate(){usage.start(101);assertThatThrownBy(()->usage.start(101)).isInstanceOf(BusinessException.class);assertThat(repo.byAssignment(101)).hasSize(1);}
 @Test void unused(){long a=usage.start(102);usage.markUnused(103);usage.finish(a);assertThat(status(1002)).isEqualTo("COMPLETED");assertThat(repo.byAssignment(103)).isEmpty();}
 @Test void allUnusedRollback(){assertThatThrownBy(()->usage.markUnused(101)).isInstanceOf(BusinessException.class);assertThat(repo.assignment(101).status().name()).isEqualTo("WAITING");}
 @Test void completedCannotRestart(){assertThatThrownBy(()->usage.start(104)).isInstanceOf(BusinessException.class);}
 @Test void completedCannotEndAgain(){assertThatThrownBy(()->usage.finish(5001)).isInstanceOf(BusinessException.class);}
 @Test void maintenanceAndRestore(){service.markMaintenance(8);assertThat(lanes.findOne(8).laneStatus()).isEqualTo("MAINTENANCE");service.restoreLane(8);assertThat(lanes.findOne(8).laneStatus()).isEqualTo("AVAILABLE");}
 @Test void reallocate(){var result=service.markMaintenance(1);assertThat(result.replaced()).isEqualTo(1);assertThat(repo.assignment(101).laneId()).isNotEqualTo(1);}
 @Test void activeFailureEndsUsage(){long id=usage.start(101);service.markMaintenance(1);assertThat(repo.usage(id).end()).isNotNull();assertThat(lanes.findOne(1).laneStatus()).isEqualTo("MAINTENANCE");assertThat(repo.assignment(101).laneId()).isEqualTo(1);}
 @Test void wholeFinish(){usage.start(102);usage.finishReservation(1002);assertThat(status(1002)).isEqualTo("COMPLETED");assertThat(repo.assignment(103).status().name()).isEqualTo("UNUSED");}
 @Test void capacity(){usage.recordAttendance(1001,5);assertThatThrownBy(()->usage.start(101)).isInstanceOf(BusinessException.class);assertThat(repo.byAssignment(101)).isEmpty();}
 @Test void addLane(){usage.start(101);usage.recordAttendance(1001,5);long id=usage.addLane(1001,5);assertThat(repo.usage(id).end()).isNull();assertThat(repo.assignments(1001)).hasSize(2);}
 @Test void additionalBeforeStart(){usage.recordAttendance(1001,5);long a=usage.assignAdditionalLane(1001,5);assertThat(repo.byAssignment(a)).isEmpty();usage.start(101);usage.start(a);assertThat(repo.assignments(1001)).hasSize(2);}
 @Test void noShowCannotStart(){jdbc.update("UPDATE RESERVATION SET status='NO_SHOW' WHERE reservation_id=1001");assertThatThrownBy(()->usage.start(101)).isInstanceOf(BusinessException.class);}
 @Test void failureKeepsAssignment(){jdbc.update("UPDATE LANE SET status='MAINTENANCE' WHERE lane_id<>1");var r=service.markMaintenance(1);assertThat(r.failedReservations()).contains(1001L);assertThat(status(1001)).isEqualTo("REALLOCATION_FAILED");assertThat(repo.assignment(101).laneId()).isEqualTo(1);}
}
