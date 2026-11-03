# 개인 Oracle 19c ↔ Windows Spring Boot 연결 절차

## 현재 환경
Windows 개발 PC → PuTTY → Ubuntu → SQL*Plus → **개인 Oracle 계정**.
SQL*Plus가 접속된다고 Windows의 Spring Boot에서도 DB에 바로 접근할 수 있는 것은 아닙니다.

## 1. 직접 연결 검사
Windows PowerShell:

```powershell
Test-NetConnection <DB_HOST> -Port 1521
```

True는 **포트에 연결될 수 있다**는 뜻일 뿐 Oracle 로그인 성공 여부는 아닙니다.

```powershell
$env:SPRING_PROFILES_ACTIVE="oracle"
$env:DB_URL="jdbc:oracle:thin:@//<DB_HOST>:1521/orcl"
$env:DB_USERNAME="<ORACLE_USER>"
$env:DB_PASSWORD="<ORACLE_PASSWORD>"
mvn spring-boot:run
```

## 2. 직접 연결이 안 될 경우
학교에서 허용할 때만 PuTTY SSH 로컬 포트 포워딩을 사용합니다.

PuTTY 기존 세션 → `Connection > SSH > Tunnels`:
- Source port: `11521`
- Destination: `<DB_HOST>:1521`
- Local → Add → SSH 세션 재접속
- 로컬 외 다른 컴퓨터의 포트 접근 허용하지 않음

Windows PowerShell:
```powershell
Test-NetConnection 127.0.0.1 -Port 11521
$env:SPRING_PROFILES_ACTIVE="oracle"
$env:DB_URL="jdbc:oracle:thin:@//127.0.0.1:11521/orcl"
$env:DB_USERNAME="<ORACLE_USER>"
$env:DB_PASSWORD="<ORACLE_PASSWORD>"
mvn spring-boot:run
```

PuTTY 접속이 끊기면 터널도 중단됩니다. SSH 호스트와 Oracle DB 호스트는 다를 수 있습니다.

## 3. Oracle 쪽 점검
SQL*Plus 개인 계정에서 다음 SQL 확인:

```sql
SELECT USER FROM DUAL;
SELECT table_name FROM user_tables
WHERE table_name IN ('MEMBER','LANE','RESERVATION','RESERVATION_LANE','USAGE');
SELECT lane_id,lane_number,ASCIISTR(lane_status) FROM LANE ORDER BY lane_id;
SELECT constraint_name,ASCIISTR(search_condition_vc)
FROM user_constraints WHERE table_name IN ('LANE','RESERVATION','USAGE')
AND constraint_type='C';
SELECT MAX(usage_id) FROM USAGE;
SELECT sequence_name FROM user_sequences WHERE sequence_name='USAGE_SEQ';
```

과거 `CK_LANE_STATUS` 및 문자 집합 오류 이력이 있으므로 **실제 CHECK를 확인하기 전에는 원본 데이터 수정 금지**.

`USAGE_SEQ`가 없고 `usage_id` 최대값이 100000 미만이며 권한이 있다면 개인 테스트 계정에서 한 번만 시퀀스를 생성:

```sql
CREATE SEQUENCE USAGE_SEQ START WITH 100000 INCREMENT BY 1 NOCACHE;
```

## 4. 데이터 보호
- 코드에 Oracle 비밀번호를 하드코딩하거나 GitHub에 업로드하지 않습니다.
- Oracle 프로필은 Spring SQL 자동 초기화를 사용하지 않습니다.
- 먼저 H2 데모 모드로 개발/테스트한 후 개인 Oracle을 연동합니다.
