-- H2 demo only: never run against Oracle.
CREATE TABLE MEMBER (
 member_id BIGINT PRIMARY KEY, name VARCHAR(50) NOT NULL, phone VARCHAR(20) NOT NULL,
 gender VARCHAR(10), join_date TIMESTAMP NOT NULL, member_grade VARCHAR(20) NOT NULL
);
CREATE TABLE LANE (
 lane_id BIGINT PRIMARY KEY, lane_number INTEGER NOT NULL UNIQUE CHECK(lane_number > 0),
 lane_status VARCHAR(20) NOT NULL,
 CONSTRAINT ck_lane_status CHECK(lane_status IN ('이용가능','점검중','사용불가'))
);
CREATE TABLE RESERVATION (
 reservation_id BIGINT PRIMARY KEY, member_id BIGINT NOT NULL,
 start_time TIMESTAMP NOT NULL, end_time TIMESTAMP NOT NULL, person_count INTEGER NOT NULL,
 reservation_status VARCHAR(20) NOT NULL,
 CONSTRAINT ck_reservation_time CHECK(start_time < end_time AND end_time <= DATEADD('MINUTE', 120, start_time)),
 CONSTRAINT ck_reservation_person_count CHECK(person_count >= 1),
 CONSTRAINT fk_reservation_member FOREIGN KEY(member_id) REFERENCES MEMBER(member_id),
 CONSTRAINT ck_reservation_status CHECK(reservation_status IN ('예약대기','예약확정','이용중','이용완료','취소'))
);
CREATE TABLE RESERVATION_LANE (
 reservation_id BIGINT NOT NULL, lane_id BIGINT NOT NULL,
 PRIMARY KEY(reservation_id,lane_id),
 FOREIGN KEY(reservation_id) REFERENCES RESERVATION(reservation_id),
 FOREIGN KEY(lane_id) REFERENCES LANE(lane_id)
);
CREATE TABLE USAGE (
 usage_id BIGINT PRIMARY KEY, reservation_id BIGINT NOT NULL, lane_id BIGINT NOT NULL,
 actual_start_time TIMESTAMP,actual_end_time TIMESTAMP,usage_status VARCHAR(20) NOT NULL,
 CONSTRAINT uq_usage_assignment UNIQUE(reservation_id,lane_id),
 FOREIGN KEY(reservation_id,lane_id) REFERENCES RESERVATION_LANE(reservation_id,lane_id),
 CONSTRAINT ck_usage_status CHECK(usage_status IN ('대기','이용중','이용완료')),
 CONSTRAINT ck_usage_time CHECK(actual_end_time IS NULL OR (actual_start_time IS NOT NULL AND actual_end_time > actual_start_time))
);
CREATE SEQUENCE USAGE_SEQ START WITH 100000 INCREMENT BY 1;
