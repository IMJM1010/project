-- Read-only queries useful before the teammate's reservation module exists.
SELECT lane_id,lane_number,ASCIISTR(lane_status) AS lane_status FROM LANE ORDER BY lane_number;
SELECT r.reservation_id,rl.lane_id,r.start_time,r.end_time,ASCIISTR(r.reservation_status) AS reservation_status
FROM RESERVATION r JOIN RESERVATION_LANE rl ON r.reservation_id=rl.reservation_id
ORDER BY r.start_time DESC;
SELECT u.usage_id,u.reservation_id,u.lane_id,u.actual_start_time,u.actual_end_time,ASCIISTR(u.usage_status) AS usage_status
FROM USAGE u ORDER BY u.usage_id DESC;
-- Reservation-lane pairs that do not have a usage record yet:
SELECT rl.reservation_id,rl.lane_id FROM RESERVATION_LANE rl
LEFT JOIN USAGE u ON u.reservation_id=rl.reservation_id AND u.lane_id=rl.lane_id
WHERE u.usage_id IS NULL ORDER BY rl.reservation_id,rl.lane_id;
