package com.example.bowling.repository;

import com.example.bowling.dto.LaneInfo;
import com.example.bowling.dto.LaneSchedule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.sql.Timestamp;

@Repository
public class LaneRepository {
    private final JdbcTemplate jdbc;
    public LaneRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<LaneInfo> findAll() {
        String sql = """
            SELECT l.lane_id, l.lane_number, l.lane_status,
                   CASE WHEN EXISTS (SELECT 1 FROM USAGE u WHERE u.lane_id=l.lane_id
                     AND u.usage_status = ? AND u.actual_end_time IS NULL)
                   THEN 1 ELSE 0 END AS occupied
            FROM LANE l ORDER BY l.lane_number
            """;
        return jdbc.query(sql, (rs,i) -> new LaneInfo(rs.getLong("lane_id"),
            rs.getInt("lane_number"), rs.getString("lane_status"),rs.getInt("occupied")==1),"이용중");
    }

    public LaneInfo findOne(long laneId) {
        List<LaneInfo> items=jdbc.query("""
            SELECT l.lane_id,l.lane_number,l.lane_status,
                   CASE WHEN EXISTS (
                       SELECT 1 FROM USAGE u WHERE u.lane_id=l.lane_id
                         AND u.usage_status=? AND u.actual_end_time IS NULL
                   ) THEN 1 ELSE 0 END AS occupied
            FROM LANE l WHERE l.lane_id=?
            """,(rs,i)->new LaneInfo(rs.getLong("lane_id"),rs.getInt("lane_number"),
                rs.getString("lane_status"),rs.getInt("occupied")==1),"이용중",laneId);
        if(items.isEmpty()) throw new com.example.bowling.service.BusinessException("존재하지 않는 레인입니다.");
        return items.get(0);
    }

    public String lockStatus(long laneId) {
        List<String> states=jdbc.query("SELECT lane_status FROM LANE WHERE lane_id=? FOR UPDATE",
                (rs,i)->rs.getString(1),laneId);
        if(states.isEmpty()) throw new com.example.bowling.service.BusinessException("존재하지 않는 레인입니다.");
        return states.get(0);
    }

    public int countActiveUsage(long laneId) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM USAGE WHERE lane_id=? AND usage_status=? AND actual_end_time IS NULL",
                Integer.class,laneId,"이용중");
        return n==null?0:n;
    }

    public int countFutureBookings(long laneId) {
        Integer n=jdbc.queryForObject("""
           SELECT COUNT(*) FROM RESERVATION_LANE rl
           JOIN RESERVATION r ON r.reservation_id=rl.reservation_id
           WHERE rl.lane_id=? AND r.end_time > ? AND r.reservation_status <> ?
           """, Integer.class, laneId, Timestamp.valueOf(java.time.LocalDateTime.now()), "취소");
        return n==null?0:n;
    }

    public int setStatus(long laneId,String newStatus) {
        return jdbc.update("UPDATE LANE SET lane_status=? WHERE lane_id=?",newStatus,laneId);
    }

    public List<LaneSchedule> schedules(long laneId) {
        String sql="""
            SELECT r.reservation_id,m.name member_name,r.start_time,r.end_time,
                   r.reservation_status,u.usage_status
            FROM RESERVATION_LANE rl
            JOIN RESERVATION r ON r.reservation_id=rl.reservation_id
            JOIN MEMBER m ON m.member_id=r.member_id
            LEFT JOIN USAGE u ON u.reservation_id=rl.reservation_id AND u.lane_id=rl.lane_id
            WHERE rl.lane_id=? ORDER BY r.start_time DESC FETCH FIRST 30 ROWS ONLY
            """;
        return jdbc.query(sql,(rs,i)->new LaneSchedule(rs.getLong("reservation_id"),
                rs.getString("member_name"),rs.getTimestamp("start_time").toLocalDateTime(),
                rs.getTimestamp("end_time").toLocalDateTime(),
                rs.getString("reservation_status"),rs.getString("usage_status")),laneId);
    }
}
