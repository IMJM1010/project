-- Read-only SQL: Oracle SQL*Plus with your PERSONAL account.
SET PAGESIZE 100
SET LINESIZE 180
SELECT USER AS ORACLE_LOGIN FROM DUAL;
SELECT table_name FROM user_tables
WHERE table_name IN ('MEMBER','LANE','RESERVATION','RESERVATION_LANE','USAGE')
ORDER BY table_name;
SELECT 'MEMBER' AS t,COUNT(*) AS cnt FROM MEMBER
UNION ALL SELECT 'LANE',COUNT(*) FROM LANE
UNION ALL SELECT 'RESERVATION',COUNT(*) FROM RESERVATION
UNION ALL SELECT 'RESERVATION_LANE',COUNT(*) FROM RESERVATION_LANE
UNION ALL SELECT 'USAGE',COUNT(*) FROM USAGE;
SELECT lane_id,lane_number,ASCIISTR(lane_status) AS status FROM LANE ORDER BY lane_number;
SELECT constraint_name,ASCIISTR(search_condition_vc) AS check_rule
FROM user_constraints
WHERE table_name IN ('LANE','USAGE','RESERVATION') AND constraint_type='C'
ORDER BY table_name,constraint_name;
SELECT sequence_name FROM user_sequences WHERE sequence_name='USAGE_SEQ';
