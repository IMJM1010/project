package com.example.bowling.service;
import com.example.bowling.repository.*;
import com.example.bowling.repository.UsageRepository.*;
import com.example.bowling.enums.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class LaneService {
 private final LaneRepository lanes;private final UsageRepository repo;private final UsageService usage;
 public LaneService(LaneRepository lanes,UsageRepository repo,UsageService usage){this.lanes=lanes;this.repo=repo;this.usage=usage;}
 public record MaintenanceResult(int replaced,List<Long> failedReservations){}
 @Transactional public MaintenanceResult markMaintenance(long id){
  lanes.lockAll();String status=lanes.findOne(id).laneStatus();LocalDateTime n=UsageService.now();
  if(status.equals("IN_USE")){
   // Active assignment is never rewritten. End its real usage first.
   UsageRepository.UsageState u=repo.listAll().stream().filter(x->x.laneId()==id&&x.actualEndTime()==null).findFirst()
    .map(x->repo.usage(x.usageId())).orElseThrow(()->new BusinessException("진행 중 이용 기록을 확인하세요."));
   Assignment a=repo.assignment(u.assignmentId());repo.lockBooking(a.reservationId());usage.finishInternal(u,a,n);usage.refresh(a.reservationId(),n);
  } else if(lanes.countActiveUsage(id)>0)throw new BusinessException("레인과 이용 상태 불일치");
  lanes.setStatus(id,LaneStatus.MAINTENANCE,n);
  int replaced=0;List<Long> failures=new ArrayList<>();
  for(Assignment a:repo.pendingOnLane(id,n)){
   Booking b=repo.lockBooking(a.reservationId());
   Set<Long> assigned=new HashSet<>();repo.assignments(b.id()).forEach(x->assigned.add(x.laneId()));
   Optional<Long> candidate=lanes.findAll().stream().filter(l->!l.laneStatus().equals("MAINTENANCE")&&!assigned.contains(l.laneId()))
    .filter(l->!repo.conflict(l.laneId(),a.start(),b.end(),b.id()))
    .filter(l->!l.occupied()||!a.start().isBefore(n))
    .map(l->l.laneId()).findFirst();
   if(candidate.isPresent()){repo.move(a.id(),candidate.get());replaced++;}
   else {repo.bookingState(b.id(),ReservationStatus.REALLOCATION_FAILED,n);failures.add(b.id());}
  }
  return new MaintenanceResult(replaced,List.copyOf(failures));
 }
 @Transactional public void restoreLane(long id){
  lanes.lockAll();if(!lanes.findOne(id).laneStatus().equals("MAINTENANCE")||lanes.countActiveUsage(id)>0)throw new BusinessException("점검 중이며 실제 이용이 없는 레인만 복구할 수 있습니다.");
  lanes.setStatus(id,LaneStatus.AVAILABLE,UsageService.now());
 }
}
