-- PERSONAL test schema only, once, after verifying USAGE_SEQ does not exist.
-- Run SELECT NVL(MAX(usage_id), 0) FROM USAGE; first.
-- If max usage_id >= 100000, choose a higher START WITH value.
-- Requires CREATE SEQUENCE permission. This does NOT modify any table structure.
CREATE SEQUENCE USAGE_SEQ START WITH 100000 INCREMENT BY 1 NOCACHE;
SELECT USAGE_SEQ.NEXTVAL FROM DUAL;
