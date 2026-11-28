-- Read-only; safe before/after table rename or partial creation.
SET LINESIZE 200
SET PAGESIZE 100
SELECT table_name FROM user_tables ORDER BY table_name;
SELECT table_name,column_id,column_name,data_type,data_precision,data_scale,char_length,char_used,nullable FROM user_tab_columns WHERE table_name IN ('MEMBER','LANE','RESERVATION','RESERVATION_LANE','USAGE') ORDER BY table_name,column_id;
SELECT table_name,constraint_name,constraint_type,status,validated FROM user_constraints WHERE table_name IN ('MEMBER','LANE','RESERVATION','RESERVATION_LANE','USAGE') OR table_name IN ('BAK_V15_MEMBER','BAK_V15_LANE','BAK_V15_RESERVATION','BAK_V15_RESERVATION_LANE','BAK_V15_USAGE') ORDER BY table_name,constraint_name;
SELECT table_name,index_name FROM user_indexes WHERE table_name IN ('MEMBER','LANE','RESERVATION','RESERVATION_LANE','USAGE','BAK_V15_MEMBER','BAK_V15_LANE','BAK_V15_RESERVATION','BAK_V15_RESERVATION_LANE','BAK_V15_USAGE') ORDER BY table_name,index_name;
SELECT table_name,column_name,generation_type FROM user_tab_identity_cols;
SELECT SESSIONTIMEZONE, DBTIMEZONE, LOCALTIMESTAMP FROM dual;
