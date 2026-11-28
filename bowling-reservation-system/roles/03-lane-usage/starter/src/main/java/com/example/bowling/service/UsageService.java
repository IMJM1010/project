package com.example.bowling.service;
import com.example.bowling.repository.*;
import com.example.bowling.repository.UsageRepository.*;
import com.example.bowling.enums.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;
import static com.example.bowling.enums.ReservationLaneStatus.*;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsageService {
 private final UsageRepository repo;
 private final LaneRepository lanes;
 private final boolean enforceWindow;
 private final int openHour;
 private final Clock clock;
 public UsageService(UsageRepository repo,LaneRepository lanes,@Value("${bowling.usage.enforce-time-window:true}") boolean enforceWindow,@Value("${bowling.business.open-hour:13}") int openHour,Clock clock){this.repo=repo;this.lanes=lanes;this.enforceWindow=enforceWindow;this.openHour=openHour;this.clock=clock;}
 LocalDateTime now(){return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));}
 private void usable(Booking b){if(b.status()!=ReservationStatus.CONFIRMED && b.status()!=ReservationStatus.IN_USE)throw new BusinessException("확정 또는 이용 중인 예약만 처리할 수 있습니다.");}
 private void window(Booking b,LocalDateTime n){
  if(enforceWindow && (n.isBefore(b.start()) || !n.isBefore(b.end())))throw new BusinessException("예약 시작부터 종료 전까지만 이용할 수 있습니다.");
 }
 private int capacity(long id){return (int)repo.assignments(id).stream().filter(a->a.status()!=UNUSED).count()*4;}
 @Transactional public void recordAttendance(long reservationId,int actual){
  lanes.lockAll();Booking b=repo.lockBooking(reservationId);usable(b);
  if(actual<1||actual>40)throw new BusinessException("실제 방문 인원은 1~40명이어야 합니다.");
  repo.attendance(reservationId,actual,now());
 }
 @Transactional public long startUsage(long assignmentId){
  lanes.lockAll();Assignment a=repo.assignment(assignmentId);Booking b=repo.lockBooking(a.reservationId());usable(b);
  LocalDateTime n=now();window(b,n);
  if(b.actual()==null||b.actual()<1)throw new BusinessException("실제 방문 인원을 먼저 확인하세요.");
  if(b.actual()>capacity(b.id()))throw new BusinessException("수용 인원을 초과합니다. 추가 레인을 배정하세요.");
  return begin(a,b,n);
 }
 private long begin(Assignment a,Booking b,LocalDateTime n){
  if(a.status()!=WAITING||!repo.byAssignment(a.id()).isEmpty())throw new BusinessException("이용대기 배정만 시작할 수 있습니다.");
  if(!lanes.findOne(a.laneId()).laneStatus().equals("AVAILABLE")||lanes.countActiveUsage(a.laneId())>0)throw new BusinessException("이용가능 레인만 시작할 수 있습니다.");
  if(enforceWindow && n.isBefore(a.start()))throw new BusinessException("배정 시작시간 이전입니다.");
  if(repo.conflict(a.laneId(),n,b.end(),b.id()))throw new BusinessException("다른 예약과 시간이 중복됩니다.");
  long id=repo.create(a.id(),n);lanes.setStatus(a.laneId(),LaneStatus.IN_USE,n);
  repo.state(a.id(),IN_USE);repo.bookingState(b.id(),ReservationStatus.IN_USE,n);return id;
 }
 @Transactional public void endUsage(long usageId){
  lanes.lockAll();UsageState u=repo.usage(usageId);Assignment a=repo.assignment(u.assignmentId());repo.lockBooking(a.reservationId());
  finishInternal(u,a,now());refresh(a.reservationId(),now());
 }
 void finishInternal(UsageState u,Assignment a,LocalDateTime n){
  if(a.status()!=IN_USE||u.end()!=null)throw new BusinessException("이용 중인 기록만 종료할 수 있습니다.");
  repo.finish(u.id(),n);repo.state(a.id(),COMPLETED);lanes.setStatus(a.laneId(),LaneStatus.AVAILABLE,n);
 }
 void refresh(long id,LocalDateTime n){
  Booking b=repo.booking(id);List<Assignment> list=repo.assignments(id);
  if(b.status()==ReservationStatus.REALLOCATION_FAILED||b.status()==ReservationStatus.CANCELLED||b.status()==ReservationStatus.NO_SHOW)return;
  boolean used=list.stream().anyMatch(a->a.status()==COMPLETED||a.status()==IN_USE);
  boolean done=list.stream().allMatch(a->a.status()==COMPLETED||a.status()==UNUSED);
  if(done&&!used)throw new BusinessException("전체 미사용 예약은 관리자 노쇼 정책에 따라 별도로 처리하세요.");
  repo.bookingState(id,done?ReservationStatus.COMPLETED:used?ReservationStatus.IN_USE:ReservationStatus.CONFIRMED,n);
 }
 @Transactional public void markUnused(long assignmentId){
  lanes.lockAll();Assignment a=repo.assignment(assignmentId);Booking b=repo.lockBooking(a.reservationId());usable(b);
  if(a.status()!=WAITING||!repo.byAssignment(a.id()).isEmpty())throw new BusinessException("이용대기 레인만 미사용 처리할 수 있습니다.");
  repo.state(a.id(),UNUSED);refresh(b.id(),now());
 }
 @Transactional public void finishReservation(long id){
  lanes.lockAll();Booking b=repo.lockBooking(id);usable(b);LocalDateTime n=now();
  for(Assignment a:repo.assignments(id)){
   if(a.status()==IN_USE)finishInternal(repo.byAssignment(a.id()).get(0),a,n);
   else if(a.status()==WAITING)repo.state(a.id(),UNUSED);
  }
  refresh(id,n);
 }
 @Transactional public void extendReservation(long id,int minutes){
  lanes.lockAll();Booking b=repo.lockBooking(id);usable(b);LocalDateTime n=now();
  if(b.status()!=ReservationStatus.IN_USE||minutes<=0||minutes>120||!n.isBefore(b.end()))throw new BusinessException("이용 중 예약만 종료 전 1~120분 연장할 수 있습니다.");
  LocalDateTime end=b.end().plusMinutes(minutes),close=b.start().toLocalDate().plusDays(1).atStartOfDay();
  if(b.start().getHour()<openHour||end.isAfter(close))throw new BusinessException("영업시간을 초과합니다.");
  if(repo.memberConflict(b.memberId(),b.start(),end,id))throw new BusinessException("회원의 다른 예약과 시간이 중복됩니다.");
  for(Assignment a:repo.assignments(id)){
   if(lanes.findOne(a.laneId()).laneStatus().equals("MAINTENANCE")||repo.conflict(a.laneId(),a.start(),end,id))throw new BusinessException("점검 또는 다음 예약과 충돌하여 전체 연장할 수 없습니다.");
  }
  repo.extend(id,end,n);
 }
 @Transactional public long assignAdditionalLane(long id,long laneId){
  lanes.lockAll();Booking b=repo.lockBooking(id);usable(b);LocalDateTime n=now();window(b,n);
  if(b.status()!=ReservationStatus.CONFIRMED||!n.isBefore(b.end())||n.isBefore(b.start()))throw new BusinessException("이용 시작 전 확정 예약만 배정할 수 있습니다.");
  if(repo.assignments(id).stream().anyMatch(a->a.laneId()==laneId))throw new BusinessException("이미 배정된 레인입니다.");
  if(!lanes.findOne(laneId).laneStatus().equals("AVAILABLE")||lanes.countActiveUsage(laneId)>0||repo.conflict(laneId,n,b.end(),id))throw new BusinessException("추가할 수 없는 레인입니다.");
  return repo.add(id,laneId,n).id();
 }
 @Transactional public long addLane(long id,long laneId){
  lanes.lockAll();Booking b=repo.lockBooking(id);usable(b);LocalDateTime n=now();window(b,n);
  if(b.status()!=ReservationStatus.IN_USE||!n.isBefore(b.end())||n.isBefore(b.start()))throw new BusinessException("이용 중 예약에만 추가할 수 있습니다.");
  if(repo.assignments(id).stream().anyMatch(a->a.laneId()==laneId))throw new BusinessException("이미 배정된 레인입니다.");
  if(!lanes.findOne(laneId).laneStatus().equals("AVAILABLE")||lanes.countActiveUsage(laneId)>0||repo.conflict(laneId,n,b.end(),id))throw new BusinessException("추가할 수 없는 레인입니다.");
  return begin(repo.add(id,laneId,n),b,n);
 }
}
