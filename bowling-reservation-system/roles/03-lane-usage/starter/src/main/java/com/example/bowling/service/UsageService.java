package com.example.bowling.service;

import com.example.bowling.repository.LaneRepository;
import com.example.bowling.repository.UsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UsageService {
    private final UsageRepository usages;
    private final LaneRepository lanes;
    private final boolean enforceWindow;

    public UsageService(UsageRepository usages,LaneRepository lanes,
           @Value("${bowling.usage.enforce-time-window:true}") boolean enforceWindow) {
        this.usages=usages;this.lanes=lanes;this.enforceWindow=enforceWindow;
    }

    @Transactional
    public long start(long reservationId,long laneId) {
        // Lock a lane before checking occupancy: serializes simultaneous starts on this lane.
        String facilityStatus=lanes.lockStatus(laneId);
        if(!"이용가능".equals(facilityStatus))
            throw new BusinessException("이용가능 상태의 레인만 시작할 수 있습니다.");
        UsageRepository.ReservationInfo reservation=usages.lockReservation(reservationId);
        if(!List.of("예약확정","이용중").contains(reservation.status()))
            throw new BusinessException("확정되었거나 이용 중인 예약만 시작할 수 있습니다.");
        if(!usages.hasAssignment(reservationId,laneId))
            throw new BusinessException("해당 예약에 배정된 레인이 아닙니다.");
        LocalDateTime now=LocalDateTime.now();
        if(enforceWindow && (now.isBefore(reservation.start().minusMinutes(30)) || !now.isBefore(reservation.end())))
            throw new BusinessException("예약 시작 30분 전부터 예약 종료 전까지만 시작할 수 있습니다.");
        if(lanes.countActiveUsage(laneId)>0)
            throw new BusinessException("이미 다른 이용이 진행 중인 레인입니다.");

        List<UsageRepository.UsageState> existing=usages.findByAssignment(reservationId,laneId);
        long id;
        if(existing.isEmpty()) id=usages.create(reservationId,laneId,now);
        else if("대기".equals(existing.get(0).status())) id=usages.beginWaiting(existing.get(0).usageId(),now);
        else throw new BusinessException("이미 시작 또는 완료한 이용 기록입니다.");
        usages.updateReservationStatus(reservationId,"이용중");
        return id;
    }

    @Transactional
    public void finish(long usageId) {
        // Keep one lock order across start(), finish(), and lane status changes:
        // LANE row -> RESERVATION row -> USAGE row.
        // The initial read only identifies the lock targets. We re-read under lock.
        UsageRepository.UsageState candidate=usages.findState(usageId);
        lanes.lockStatus(candidate.laneId());
        usages.lockReservation(candidate.reservationId());
        UsageRepository.UsageState current=usages.lockUsage(usageId);
        if(current.laneId()!=candidate.laneId() || current.reservationId()!=candidate.reservationId())
            throw new BusinessException("이용 기록 정보가 변경되었습니다. 다시 시도하세요.");
        if(!"이용중".equals(current.status()) || current.start()==null)
            throw new BusinessException("현재 이용 중인 기록만 종료할 수 있습니다.");
        usages.finish(usageId,LocalDateTime.now());
        if(usages.countUnfinishedAssignments(current.reservationId())==0)
            usages.updateReservationStatus(current.reservationId(),"이용완료");
        else usages.updateReservationStatus(current.reservationId(),"이용중");
    }
}
