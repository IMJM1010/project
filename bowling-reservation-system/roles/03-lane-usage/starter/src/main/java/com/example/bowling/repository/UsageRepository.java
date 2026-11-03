package com.example.bowling.repository;

import com.example.bowling.dto.UsageInfo;
import com.example.bowling.service.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class UsageRepository {
    private final JdbcTemplate jdbc;
    private final String sequenceSql;
    public UsageRepository(JdbcTemplate jdbc,@Value("${bowling.usage.id-sql}") String sequenceSql) {
        this.jdbc=jdbc; this.sequenceSql=sequenceSql;
    }

    public record ReservationInfo(String status, LocalDateTime start,LocalDateTime end) {}
    public record UsageState(long usageId,long reservationId,long laneId,String status,LocalDateTime start) {}

    public ReservationInfo lockReservation(long reservationId) {
        List<ReservationInfo> list=jdbc.query("SELECT reservation_status,start_time,end_time FROM RESERVATION WHERE reservation_id=? FOR UPDATE",
            (rs,i)->new ReservationInfo(rs.getString(1),rs.getTimestamp(2).toLocalDateTime(),
                                         rs.getTimestamp(3).toLocalDateTime()),reservationId);
        if(list.isEmpty()) throw new BusinessException("존재하지 않는 예약입니다.");
        return list.get(0);
    }

    public boolean hasAssignment(long reservationId,long laneId) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM RESERVATION_LANE WHERE reservation_id=? AND lane_id=?",
            Integer.class,reservationId,laneId);
        return count!=null && count>0;
    }

    public List<UsageState> findByAssignment(long reservationId,long laneId) {
        return jdbc.query("SELECT usage_id,reservation_id,lane_id,usage_status,actual_start_time FROM USAGE WHERE reservation_id=? AND lane_id=?",
            (rs,i)->new UsageState(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),
                 rs.getTimestamp(5)==null?null:rs.getTimestamp(5).toLocalDateTime()),reservationId,laneId);
    }

    // Non-locking lookup: use only to identify which lane/reservation to lock first.
    public UsageState findState(long usageId) {
        List<UsageState> items=jdbc.query(
            "SELECT usage_id,reservation_id,lane_id,usage_status,actual_start_time FROM USAGE WHERE usage_id=?",
            (rs,i)->new UsageState(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),
                    rs.getTimestamp(5)==null?null:rs.getTimestamp(5).toLocalDateTime()),usageId);
        if(items.isEmpty()) throw new BusinessException("이용 기록이 없습니다.");
        return items.get(0);
    }

    public UsageState lockUsage(long usageId) {
        List<UsageState> items=jdbc.query("SELECT usage_id,reservation_id,lane_id,usage_status,actual_start_time FROM USAGE WHERE usage_id=? FOR UPDATE",
            (rs,i)->new UsageState(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),
                    rs.getTimestamp(5)==null?null:rs.getTimestamp(5).toLocalDateTime()),usageId);
        if(items.isEmpty()) throw new BusinessException("이용 기록이 없습니다.");
        return items.get(0);
    }

    public long create(long reservationId,long laneId,LocalDateTime now) {
        Long id=jdbc.queryForObject(sequenceSql,Long.class);
        if(id==null) throw new BusinessException("이용 기록번호를 발급하지 못했습니다.");
        jdbc.update("INSERT INTO USAGE(usage_id,reservation_id,lane_id,actual_start_time,actual_end_time,usage_status) VALUES(?,?,?,?,?,?)",
                id,reservationId,laneId,Timestamp.valueOf(now),null,"이용중");
        return id;
    }

    public long beginWaiting(long usageId,LocalDateTime now) {
        int n=jdbc.update("UPDATE USAGE SET actual_start_time=?,actual_end_time=NULL,usage_status=? WHERE usage_id=? AND usage_status=?",
                Timestamp.valueOf(now),"이용중",usageId,"대기");
        if(n!=1) throw new BusinessException("대기 기록을 시작 상태로 변경하지 못했습니다.");
        return usageId;
    }

    public void finish(long usageId,LocalDateTime now) {
        int n=jdbc.update("UPDATE USAGE SET actual_end_time=?,usage_status=? WHERE usage_id=? AND usage_status=? AND actual_start_time < ?",
                Timestamp.valueOf(now),"이용완료",usageId,"이용중",Timestamp.valueOf(now));
        if(n!=1) throw new BusinessException("이용 종료가 불가능합니다. 이용 상태와 시간을 확인하세요.");
    }

    public void updateReservationStatus(long reservationId,String status) {
        jdbc.update("UPDATE RESERVATION SET reservation_status=? WHERE reservation_id=?",status,reservationId);
    }

    public int countUnfinishedAssignments(long reservationId) {
        Integer n=jdbc.queryForObject("""
            SELECT COUNT(*) FROM RESERVATION_LANE rl
            LEFT JOIN USAGE u ON u.reservation_id=rl.reservation_id AND u.lane_id=rl.lane_id
            WHERE rl.reservation_id=? AND (u.usage_id IS NULL OR u.usage_status <> ?)
            """,Integer.class,reservationId,"이용완료");
        return n==null?0:n;
    }

    public List<UsageInfo> listAll() {
        String sql="""
            SELECT u.usage_id,u.reservation_id,u.lane_id,l.lane_number,m.name member_name,
                   u.actual_start_time,u.actual_end_time,u.usage_status
            FROM USAGE u JOIN LANE l ON l.lane_id=u.lane_id
            JOIN RESERVATION r ON r.reservation_id=u.reservation_id
            JOIN MEMBER m ON m.member_id=r.member_id
            ORDER BY u.usage_id DESC FETCH FIRST 100 ROWS ONLY
            """;
        return jdbc.query(sql,(rs,i)->new UsageInfo(rs.getLong("usage_id"),rs.getLong("reservation_id"),
            rs.getLong("lane_id"),rs.getInt("lane_number"),rs.getString("member_name"),
            rs.getTimestamp("actual_start_time")==null?null:rs.getTimestamp("actual_start_time").toLocalDateTime(),
            rs.getTimestamp("actual_end_time")==null?null:rs.getTimestamp("actual_end_time").toLocalDateTime(),
            rs.getString("usage_status")));
    }
}
