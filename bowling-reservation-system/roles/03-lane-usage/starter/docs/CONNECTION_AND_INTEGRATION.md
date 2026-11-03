# 3번 담당: Oracle 연결과 4인 프로젝트 병합 가이드

## 1. 기존 환경 (현재까지 확인된 정보)
- Windows PC → PuTTY SSH → Ubuntu Linux 쉘 → SQL*Plus 개인 Oracle 계정
- 사용자는 SQL*Plus에서 `sqlplus [Oracle계정]@[DB호스트]:1521/orcl` 형태로 Oracle 19c 접속에 성공함
- **PuTTY로 접속한 SSH 서버와 Oracle DB 서버는 다를 수 있음**
- 로컬 개발은 Windows IntelliJ IDEA 또는 VS Code에서 수행. PuTTY를 Java 개발 IDE로 쓰지 않음.

## 2. 네트워크 연결 방법 선택
### A. 직접 연결 (Windows에서 DB 포트가 허용된 경우)
Windows PowerShell:

```powershell
Test-NetConnection <DB_HOST> -Port 1521
$env:SPRING_PROFILES_ACTIVE="oracle"
$env:DB_URL="jdbc:oracle:thin:@//<DB_HOST>:1521/orcl"
$env:DB_USERNAME="<ORACLE_USER>"
$env:DB_PASSWORD="<ORACLE_PASSWORD>"
mvn spring-boot:run
```

`TcpTestSucceeded=True`는 네트워크 포트 도달성만 뜻하며 DB 인증 성공을 의미하지 않음.

### B. PuTTY Local SSH 터널 (학교가 포워딩을 허용한 경우)
1. PuTTY 기존 SSH 세션 선택 → `Connection > SSH > Tunnels`.
2. `Source port`: `127.0.0.1:11521` (또는 UI에서 `11521` 입력; 로컬만 바인딩).
3. `Destination`: `<DB_HOST>:1521` (`localhost:1521`이 아님; DB가 별도 서버이면 실제 DB 호스트).
4. `Local` 선택 → `Add` → 저장 → 세션을 새로 열어 Ubuntu 로그인 유지.
5. Windows PowerShell: `Test-NetConnection 127.0.0.1 -Port 11521`.
6. 성공 후:

```powershell
$env:SPRING_PROFILES_ACTIVE="oracle"
$env:DB_URL="jdbc:oracle:thin:@//127.0.0.1:11521/orcl"
$env:DB_USERNAME="<ORACLE_USER>"
$env:DB_PASSWORD="<ORACLE_PASSWORD>"
mvn spring-boot:run
```

보안: `Local ports accept connections from other hosts` 선택 금지. SSH 포트 포워딩 권한/운영 정책이 허용될 때만 사용.

### C. 두 방법 모두 불가능할 때
`demo` 모드(H2 로컬 메모리 DB)로 개발과 UI 테스트 진행. 운영망 접근과 무관하게 기능을 구현하고 팀 환경이 준비되면 Oracle 전환.

## 3. 데이터베이스 확인 (Oracle SQL*Plus)
개인 계정에서 `@sql/00_check_oracle.sql`을 실행하거나 내용을 SQL*Plus에 한 문장씩 입력.

- 계정, 5개 테이블, 행 수, 레인 시설 상태, 제약조건, `USAGE_SEQ` 유무 확인.
- 현재 더미데이터 입력 시 **CK_LANE_STATUS 실패 이력**이 있으므로, 읽기 테스트 성공을 먼저 확인.
- 실제 상태변경/이용 시작 테스트 전 CHECK 조건이 올바른 한글 값으로 정의되었는지 진단할 것.

## 4. USAGE_SEQ
기존 ERD의 `USAGE.usage_id`는 자동증가 컬럼이 아님. 본 모듈은 `USAGE_SEQ.NEXTVAL` 사용.
- 개인 Oracle 계정에서 `SELECT MAX(usage_id) FROM USAGE;` 결과 확인.
- 시퀀스가 없고 기존 ID가 100000 미만이면 `sql/01_create_usage_sequence_once.sql` 실행.
- 이미 시퀀스가 있으면 **재생성하지 말 것**. 팀원과 ID 생성 방식을 통일할 것.
- CREATE SEQUENCE 권한이 없으면 1번 DB 담당자와 계정 권한/ID 정책 상의.

## 5. 2번 예약 담당자 없이 테스트
- **레인 조회/상태 변경**: LANE에 데이터만 있으면 독립적으로 가능.
- **이용 기록 목록**: USAGE에 예제 데이터가 있으면 독립적으로 가능.
- **이용 시작/종료**: RESERVED assignment가 필요. H2 demo에 예제 `RESERVATION`과 `RESERVATION_LANE`이 내장되어 있어 2번의 코드는 불필요.
- Oracle에 테스트 데이터가 없다면 개인 테스트 계정에 2번 담당과 충돌하지 않는 시험용 예약/배정 데이터를 준비해야 함. 기존 데이터/테이블을 무단 삭제하지 말 것.

## 6. 추천 협의 인터페이스
- GET `/lanes` (목록), GET `/lanes/{id}` (시설 상태/예약 일정)
- POST `/lanes/{id}/status` 시설 상태 변경
- GET `/usages` (이용 기록)
- POST `/usages/start` (reservationId, laneId)
- POST `/usages/{id}/end` (이용 기록 ID)

### 상태전이 추천 (2번과 반드시 확정)
예약확정 → (레인 1개 이상 시작) 이용중 → (배정 레인 전부 종료) 이용완료.
취소된 예약은 시작 불가. 이미 시작한 예약의 취소는 별도 업무 정책 필요.
시설 상태(`LANE`)와 특정 시각의 점유(`USAGE`)를 혼동하지 말 것.

## 7. 공통 프로젝트에 코드를 병합할 때
1. 기존 팀 프로젝트의 공통 Java 패키지명을 확인.
2. `controller/LaneController.java`, `controller/UsageController.java`, `service/*`, `repository/*`, `dto/*`를 공통 `src/main/java` 아래로 이동 (패키지 import 조정).
3. Thymeleaf `templates/lanes`, `templates/usages`를 공통 `resources/templates`로 이동.
4. `static/css/style.css`는 4번 UI 담당자의 공통 CSS와 병합(중복 덮어쓰기 금지).
5. `LaneUsageApplication.java`와 별도 `pom.xml`은 **공통 팀 프로젝트에 중복 추가하지 말 것**.
6. `application-oracle.properties`는 공통 환경 설정 파일과 통합. 비밀번호는 환경변수로.
7. 2번의 예약 상태 변경 로직과 본 모듈의 상태 변경/트랜잭션 경계를 공동 확인.
8. 충돌 없는 기능 브랜치 (`feature/lane-usage`)에서 커밋 후 PR 작성.

## 8. 코드 동작 범위 및 주의
- 해당 모듈은 **관리자용 시연/수업 과제**. 로그인/권한 및 CSRF 등 서비스 보안 기능은 구현되어 있지 않음. 공개 인터넷 서버에 그대로 배포 금지.
- 동일 레인의 이용 중 중복을 방지하기 위해 레인 행을 잠근 후 상태를 검증함.
- 예약 시작 시간 30분 전부터 종료 전까지만 이용 시작하도록 Oracle 모드에서 검증함 (시연 시 실제 시간과 예약시간 주의).
- 로컬 시간대와 DB 서버 시간대 차이는 통합 시 검토 필요.
- 과거 더미데이터의 `이용중` 상태와 실제 현재 점유 현황은 일치하지 않을 수 있음.
- `@Transactional`로 이용 기록과 예약 상태를 함께 갱신함.
