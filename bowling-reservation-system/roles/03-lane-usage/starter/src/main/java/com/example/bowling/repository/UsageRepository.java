package com.example.bowling.repository;
import com.example.bowling.dto.UsageInfo;
import com.example.bowling.enums.*;
import com.example.bowling.service.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class UsageRepository {
 private final JdbcTemplate jdbc;
 public UsageRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Booking(long id,long memberId,ReservationStatus status,LocalDateTime start,LocalDateTime end,int people,Integer actual){}
 public record Assignment(long id,long reservationId,long laneId,LocalDateTime start,ReservationLaneStatus status){}
 public record UsageState(long id,long assignmentId,LocalDateTime start,LocalDateTime end){}
 private static Timestamp ts(LocalDateTime t){return Timestamp.valueOf(t);}
 private static LocalDateTime dt(java.sql.ResultSet rs,int i)throws java.sql.SQLException{Timestamp t=rs.getTimestamp(i);return t==null?null:t.toLocalDateTime();}
 private static <T>T one(List<T> l,String message){return l.stream().findFirst().orElseThrow(()->new BusinessException(message));}
 public Booking booking(long id){
  return one(jdbc.query("SELECT reservation_id,member_id,status,reservation_start_time,reservation_end_time,person_count,actual_person_count FROM RESERVATION WHERE reservation_id=?",
   (rs,i)->new Booking(rs.getLong(1),rs.getLong(2),ReservationStatus.valueOf(rs.getString(3)),dt(rs,4),dt(rs,5),rs.getInt(6),rs.getObject(7,Integer.class)),id),"예약이 없습니다.");
 }
 public Booking lockBooking(long id){
  Booking b=booking(id);
  jdbc.queryForObject("SELECT member_id FROM MEMBER WHERE member_id=? FOR UPDATE",Long.class,b.memberId());
  jdbc.queryForObject("SELECT reservation_id FROM RESERVATION WHERE reservation_id=? FOR UPDATE",Long.class,id);
  return booking(id);
 }
 private Assignment assignmentRow(java.sql.ResultSet rs,int i)throws java.sql.SQLException{
  return new Assignment(rs.getLong(1),rs.getLong(2),rs.getLong(3),dt(rs,4),ReservationLaneStatus.valueOf(rs.getString(5)));
 }
 private static final String A="SELECT reservation_lane_id,reservation_id,lane_id,assigned_start_time,usage_status FROM RESERVATION_LANE";
 public Assignment assignment(long id){return one(jdbc.query(A+" WHERE reservation_lane_id=?",this::assignmentRow,id),"배정된 레인이 아닙니다.");}
 public Assignment assignment(long reservation,long lane){return one(jdbc.query(A+" WHERE reservation_id=? AND lane_id=?",this::assignmentRow,reservation,lane),"배정된 레인이 아닙니다.");}
 public List<Assignment> assignments(long reservation){return jdbc.query(A+" WHERE reservation_id=? ORDER BY reservation_lane_id",this::assignmentRow,reservation);}
 public List<Assignment> pendingOnLane(long lane,LocalDateTime now){return jdbc.query("""
 SELECT rl.reservation_lane_id,rl.reservation_id,rl.lane_id,rl.assigned_start_time,rl.usage_status
 FROM RESERVATION_LANE rl JOIN RESERVATION r ON r.reservation_id=rl.reservation_id
 WHERE rl.lane_id=? AND rl.usage_status='WAITING' AND r.status IN ('CONFIRMED','IN_USE','REALLOCATION_FAILED')
 AND r.reservation_end_time>? ORDER BY rl.assigned_start_time,rl.reservation_lane_id
 """,this::assignmentRow,lane,ts(now));}
 public List<UsageState> byAssignment(long id){return jdbc.query("SELECT usage_id,reservation_lane_id,actual_start_time,actual_end_time FROM USAGE WHERE reservation_lane_id=?",(rs,i)->new UsageState(rs.getLong(1),rs.getLong(2),dt(rs,3),dt(rs,4)),id);}
 public UsageState usage(long id){return one(jdbc.query("SELECT usage_id,reservation_lane_id,actual_start_time,actual_end_time FROM USAGE WHERE usage_id=?",(rs,i)->new UsageState(rs.getLong(1),rs.getLong(2),dt(rs,3),dt(rs,4)),id),"이용 기록이 없습니다.");}
 public long create(long assignmentId,LocalDateTime now){
  jdbc.update("INSERT INTO USAGE(reservation_lane_id,actual_start_time) VALUES(?,?)",assignmentId,ts(now));
  return byAssignment(assignmentId).get(0).id();
 }
 public void finish(long id,LocalDateTime now){
  if(jdbc.update("UPDATE USAGE SET actual_end_time=? WHERE usage_id=? AND actual_end_time IS NULL AND actual_start_time<=?",ts(now),id,ts(now))!=1)
   throw new BusinessException("이미 종료되었거나 종료시간이 올바르지 않습니다.");
 }
 public void state(long assignmentId,ReservationLaneStatus s){jdbc.update("UPDATE RESERVATION_LANE SET usage_status=? WHERE reservation_lane_id=?",s.name(),assignmentId);}
 public void bookingState(long id,ReservationStatus s,LocalDateTime now){jdbc.update("UPDATE RESERVATION SET status=?,updated_at=? WHERE reservation_id=?",s.name(),ts(now),id);}
 public void attendance(long id,int n,LocalDateTime now){jdbc.update("UPDATE RESERVATION SET actual_person_count=?,updated_at=? WHERE reservation_id=?",n,ts(now),id);}
 public void extend(long id,LocalDateTime end,LocalDateTime now){jdbc.update("UPDATE RESERVATION SET reservation_end_time=?,updated_at=? WHERE reservation_id=?",ts(end),ts(now),id);}
 public void move(long assignmentId,long lane){jdbc.update("UPDATE RESERVATION_LANE SET lane_id=? WHERE reservation_lane_id=? AND usage_status='WAITING'",lane,assignmentId);}
 public Assignment add(long reservation,long lane,LocalDateTime now){
  jdbc.update("INSERT INTO RESERVATION_LANE(reservation_id,lane_id,assigned_start_time,usage_status) VALUES(?,?,?,'WAITING')",reservation,lane,ts(now));
  return assignment(reservation,lane);
 }
 public boolean conflict(long lane,LocalDateTime start,LocalDateTime end,long excludedReservation){
  return jdbc.queryForObject("""
  SELECT COUNT(*) FROM RESERVATION_LANE rl JOIN RESERVATION r ON r.reservation_id=rl.reservation_id
  WHERE rl.lane_id=? AND r.reservation_id<>? AND r.status IN ('CONFIRMED','IN_USE','COMPLETED','REALLOCATION_FAILED')
  AND rl.assigned_start_time<? AND r.reservation_end_time>?
  """,Integer.class,lane,excludedReservation,ts(end),ts(start))>0;
 }
 public List<UsageInfo> listAll(){return jdbc.query("""
 SELECT u.usage_id,rl.reservation_lane_id,r.reservation_id,l.lane_id,l.lane_number,m.member_name,u.actual_start_time,u.actual_end_time,rl.usage_status
 FROM USAGE u JOIN RESERVATION_LANE rl ON rl.reservation_lane_id=u.reservation_lane_id JOIN LANE l ON l.lane_id=rl.lane_id
 JOIN RESERVATION r ON r.reservation_id=rl.reservation_id JOIN MEMBER m ON m.member_id=r.member_id
 ORDER BY u.usage_id DESC FETCH FIRST 100 ROWS ONLY
 """,(rs,i)->new UsageInfo(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getInt(5),rs.getString(6),dt(rs,7),dt(rs,8),rs.getString(9)));}
}
