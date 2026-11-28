-- SQL*Plus: opt-in personal test fixture, not team/shared or production data.
-- Run only after canonical schema + ten AVAILABLE lanes are created.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET SQLBLANKLINES ON
SET DEFINE ON
SET VERIFY OFF
ALTER SESSION SET TIME_ZONE = 'Asia/Seoul';
ACCEPT admin_hash CHAR PROMPT 'Enter locally generated BCrypt hash: ' HIDE
DECLARE
 n NUMBER;
 admin_id NUMBER;
 m1 NUMBER;
 m2 NUMBER;
 m3 NUMBER;
 r1 NUMBER;
 r2 NUMBER;
 r3 NUMBER;
 a3 NUMBER;
 h VARCHAR2(255) := '&admin_hash';
 now_ts TIMESTAMP := LOCALTIMESTAMP;
 slot_start TIMESTAMP;
 slot_end TIMESTAMP;
 hist_start TIMESTAMP;
BEGIN
 SELECT (SELECT COUNT(*) FROM MEMBER)+(SELECT COUNT(*) FROM RESERVATION)+(SELECT COUNT(*) FROM RESERVATION_LANE)+(SELECT COUNT(*) FROM USAGE) INTO n FROM dual;
 IF n<>0 THEN RAISE_APPLICATION_ERROR(-20020,'Test tables must be empty'); END IF;
 SELECT COUNT(*) INTO n FROM LANE WHERE lane_id=lane_number AND lane_id BETWEEN 1 AND 10 AND status='AVAILABLE';
 IF n<>10 THEN RAISE_APPLICATION_ERROR(-20021,'Expected ten AVAILABLE lanes'); END IF;
 IF NOT REGEXP_LIKE(h,'^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$') THEN RAISE_APPLICATION_ERROR(-20022,'Expected BCrypt hash'); END IF;
 slot_start := CAST(TRUNC(CAST(now_ts AS DATE),'HH') AS TIMESTAMP);
 IF EXTRACT(MINUTE FROM now_ts)>=30 THEN slot_start:=slot_start+INTERVAL '30' MINUTE; END IF;
 IF EXTRACT(HOUR FROM now_ts)<13 THEN slot_start:=CAST(TRUNC(CAST(now_ts AS DATE)) AS TIMESTAMP)+INTERVAL '13' HOUR; END IF;
 slot_end:=LEAST(slot_start+INTERVAL '90' MINUTE,CAST(TRUNC(CAST(slot_start AS DATE))+1 AS TIMESTAMP));
 hist_start:=CAST(TRUNC(CAST(now_ts AS DATE))-1 AS TIMESTAMP)+INTERVAL '13' HOUR;
 INSERT INTO MEMBER(login_id,password_hash,member_name,phone_number,role) VALUES('role3_test_admin',h,'Role3 Test Admin','01000000000','ADMIN') RETURNING member_id INTO admin_id;
 INSERT INTO MEMBER(login_id,password_hash,member_name,phone_number) VALUES('role3_test_01','disabled-test','Test Member 01','01000000001') RETURNING member_id INTO m1;
 INSERT INTO MEMBER(login_id,password_hash,member_name,phone_number) VALUES('role3_test_02','disabled-test','Test Member 02','01000000002') RETURNING member_id INTO m2;
 INSERT INTO MEMBER(login_id,password_hash,member_name,phone_number) VALUES('role3_test_03','disabled-test','Test Member 03','01000000003') RETURNING member_id INTO m3;
 INSERT INTO RESERVATION(member_id,reservation_start_time,reservation_end_time,person_count,actual_person_count) VALUES(m1,slot_start,slot_end,3,3) RETURNING reservation_id INTO r1;
 INSERT INTO RESERVATION(member_id,reservation_start_time,reservation_end_time,person_count,actual_person_count) VALUES(m2,slot_start,slot_end,6,6) RETURNING reservation_id INTO r2;
 INSERT INTO RESERVATION(member_id,reservation_start_time,reservation_end_time,person_count,actual_person_count,status) VALUES(m3,hist_start,hist_start+INTERVAL '90' MINUTE,2,2,'COMPLETED') RETURNING reservation_id INTO r3;
 INSERT INTO RESERVATION_LANE(reservation_id,lane_id,assigned_start_time) VALUES(r1,1,slot_start);
 INSERT INTO RESERVATION_LANE(reservation_id,lane_id,assigned_start_time) VALUES(r2,2,slot_start);
 INSERT INTO RESERVATION_LANE(reservation_id,lane_id,assigned_start_time) VALUES(r2,3,slot_start);
 INSERT INTO RESERVATION_LANE(reservation_id,lane_id,assigned_start_time,usage_status) VALUES(r3,4,hist_start,'COMPLETED') RETURNING reservation_lane_id INTO a3;
 INSERT INTO USAGE(reservation_lane_id,actual_start_time,actual_end_time) VALUES(a3,hist_start,hist_start+INTERVAL '80' MINUTE);
 UPDATE LANE SET status='MAINTENANCE',updated_at=now_ts WHERE lane_id IN (9,10);
END;
/
UNDEFINE admin_hash
COMMIT;
SELECT reservation_id,reservation_start_time,reservation_end_time,status FROM RESERVATION ORDER BY reservation_id;
WHENEVER SQLERROR CONTINUE
