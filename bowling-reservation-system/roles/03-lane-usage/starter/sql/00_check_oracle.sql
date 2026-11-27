-- Read-only preflight for v1.5
SELECT table_name,column_name,data_type,nullable FROM user_tab_columns WHERE table_name IN ('MEMBER','LANE','RESERVATION','RESERVATION_LANE','USAGE') ORDER BY table_name,column_id;
SELECT table_name,constraint_name,constraint_type,status FROM user_constraints WHERE table_name IN ('MEMBER','LANE','RESERVATION','RESERVATION_LANE','USAGE') ORDER BY table_name,constraint_name;
SELECT table_name,column_name,generation_type FROM user_tab_identity_cols;
SELECT lane_id,lane_number,status,updated_at FROM LANE ORDER BY lane_number;
