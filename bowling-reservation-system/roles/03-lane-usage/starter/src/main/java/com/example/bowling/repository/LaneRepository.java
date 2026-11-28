package com.example.bowling.repository;
import com.example.bowling.dto.*;
import com.example.bowling.service.BusinessException;
import com.example.bowling.enums.LaneStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class LaneRepository {
 private final JdbcTemplate jdbc;
 public LaneRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static final String SELECT="SELECT lane_id,lane_number,status,updated_at FROM LANE";
 private LaneInfo map(java.sql.ResultSet rs,int i) throws java.sql.SQLException {
  String s=rs.getString("status");
  return new LaneInfo(rs.getLong("lane_id"),rs.getInt("lane_number"),s,"IN_USE".equals(s),rs.getTimestamp("updated_at").toLocalDateTime());
 }
 public List<LaneInfo> findAll(){return jdbc.query(SELECT+" ORDER BY lane_number",this::map);}
 public LaneInfo findOne(long id){return jdbc.query(SELECT+" WHERE lane_id=?",this::map,id).stream().findFirst().orElseThrow(()->new BusinessException("존재하지 않는 레인입니다."));}
 // All scheduling writers must acquire this same lock in this same order.
 public void lockAll(){
  List<Long> ids=jdbc.query("SELECT lane_id FROM LANE ORDER BY lane_id",(rs,i)->rs.getLong(1));
  if(!ids.equals(java.util.stream.LongStream.rangeClosed(1,10).boxed().toList())) throw new BusinessException("레인 초기 데이터 10개를 확인하세요.");
  for(long id:ids) jdbc.queryForObject("SELECT lane_id FROM LANE WHERE lane_id=? FOR UPDATE",Long.class,id);
 }
 public int countActiveUsage(long id){return jdbc.queryForObject("""
 SELECT COUNT(*) FROM USAGE u JOIN RESERVATION_LANE rl ON rl.reservation_lane_id=u.reservation_lane_id
 WHERE rl.lane_id=? AND u.actual_end_time IS NULL
 """,Integer.class,id);}
 public void setStatus(long id,LaneStatus status,LocalDateTime now){
  if(jdbc.update("UPDATE LANE SET status=?,updated_at=? WHERE lane_id=?",status.name(),Timestamp.valueOf(now),id)!=1)
   throw new BusinessException("레인 상태 변경 실패");
 }
 public List<LaneSchedule> schedules(long id){
  return jdbc.query("""
  SELECT rl.reservation_lane_id,r.reservation_id,m.member_name,rl.assigned_start_time,r.reservation_end_time,r.status,rl.usage_status,r.person_count,r.actual_person_count
  FROM RESERVATION_LANE rl JOIN RESERVATION r ON r.reservation_id=rl.reservation_id
  JOIN MEMBER m ON m.member_id=r.member_id WHERE rl.lane_id=?
  ORDER BY rl.assigned_start_time DESC FETCH FIRST 30 ROWS ONLY
  """,(rs,i)->new LaneSchedule(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getTimestamp(4).toLocalDateTime(),rs.getTimestamp(5).toLocalDateTime(),rs.getString(6),rs.getString(7),rs.getInt(8),(Integer)rs.getObject(9,Integer.class)),id);
 }
}
