package com.example.bowling.service;

import com.example.bowling.repository.LaneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class LaneService {
    private static final Set<String> ALLOWED=Set.of("이용가능","점검중","사용불가");
    private final LaneRepository lanes;
    public LaneService(LaneRepository lanes){this.lanes=lanes;}

    @Transactional
    public void updateStatus(long laneId,String next) {
        if(!ALLOWED.contains(next)) throw new BusinessException("허용되지 않은 시설 상태입니다.");
        String old=lanes.lockStatus(laneId);
        if(old.equals(next)) return;
        if(!"이용가능".equals(next)) {
            if(lanes.countActiveUsage(laneId)>0)
                throw new BusinessException("현재 이용 중인 레인을 점검/사용불가로 변경할 수 없습니다.");
            if(lanes.countFutureBookings(laneId)>0)
                throw new BusinessException("미래 예약이 존재합니다. 예약 담당자와 먼저 조정하세요.");
        }
        if(lanes.setStatus(laneId,next)!=1) throw new BusinessException("레인 상태 변경에 실패했습니다.");
    }
}
