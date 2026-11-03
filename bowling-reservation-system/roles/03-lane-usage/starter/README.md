# 볼링장 3번 담당: 레인·이용 기록 관리 (V2 · 독립 실행용)

**기존 5개 테이블은 유지**, 2번 담당자 예약 CRUD 없이 개발 가능한 레인·이용 기능만 모은 예제입니다.

## 구현된 기능
- `/lanes`: 전체 레인 및 현재 이용 중 여부
- `/lanes/{id}`: 해당 레인의 예약/이용 일정, 시설 상태 변경, 이용 시작 버튼
- `/usages`: 실제 이용 이력, 이용 종료 버튼
- `LANE` 시설 상태와 `USAGE` 실제 점유 상태를 별도로 관리
- 예약·레인 배정이 없는 이용 시작 차단
- 이용 중 중복 시작 방지, 동일 예약·레인의 USAGE 중복 방지
- 여러 레인 예약에서 모든 레인이 완료되면 예약 전체 `이용완료` 전환
- 각 변경은 Spring JDBC + `@Transactional`로 처리

## 실행 요건
- Java 17 이상, Maven (또는 IntelliJ의 Maven 기능), 인터넷 연결(처음 Maven 의존성 다운로드 시)
- 예시: Spring Boot 3.5.7, Spring JDBC, Thymeleaf, Oracle JDBC, H2

## 1. 혼자 개발: 데모 모드 (추천)
터미널에서 프로젝트 폴더로 이동 후:

```bash
mvn spring-boot:run
```

브라우저: http://localhost:8080/lanes

H2 메모리 DB에 **회원 3, 레인 10, 예약 3, 예약-레인 4, 완료 이용 기록 1**이 자동 입력됩니다.
- 1번 레인 / 예약 1001: 3명, `예약확정` → 이용 시작 테스트 가능.
- 2·3번 레인 / 예약 1002: 6명, `예약확정` → 다중 레인 테스트 가능.
- 4번 레인 / 예약 1003: 이미 `이용완료`.
- 9번 점검중, 10번 사용불가.

데모 DB는 서버를 재시작하면 초기화됩니다. 이 더미 데이터는 Oracle에 자동으로 입력되지 않습니다.

## 2. 개인 Oracle 연결
### 2-A. 직접 연결되는 경우 (Windows PowerShell)
```powershell
Test-NetConnection <DB_HOST> -Port 1521
$env:SPRING_PROFILES_ACTIVE="oracle"
$env:DB_URL="jdbc:oracle:thin:@//<DB_HOST>:1521/orcl"
$env:DB_USERNAME="<ORACLE_USER>"
$env:DB_PASSWORD="<ORACLE_PASSWORD>"
mvn spring-boot:run
```

### 2-B. PuTTY SSH 터널이 허용된 경우
PuTTY의 `Connection > SSH > Tunnels`: Source `127.0.0.1:11521`, Destination `<DB_HOST>:1521`, Local, Add.
PuTTY에 로그인한 상태로 유지한 후 Windows PowerShell에서:

```powershell
Test-NetConnection 127.0.0.1 -Port 11521
$env:SPRING_PROFILES_ACTIVE="oracle"
$env:DB_URL="jdbc:oracle:thin:@//127.0.0.1:11521/orcl"
$env:DB_USERNAME="<ORACLE_USER>"
$env:DB_PASSWORD="<ORACLE_PASSWORD>"
mvn spring-boot:run
```

**학교 SSH 포워딩 정책이 허용될 경우에만 사용.** DB 계정은 SQL*Plus의 개인 Oracle 계정이고, SSH 계정과 별개입니다.

### Oracle에 필요한 기존 데이터
- LANE에 1개 이상 등록, 기존 테이블들 및 CHECK 제약조건 정상
- 이용 시작 시 RESERVATION과 RESERVATION_LANE에 유효 배정이 있어야 함
- USAGE ID 발급용 `USAGE_SEQ` 생성 필요 (sql/01_create_usage_sequence_once.sql 안내 참고)
- Oracle 프로필에서는 **스키마 생성·더미데이터 자동 실행 안 함** (`spring.sql.init.mode=never`)

## 3. 팀 프로젝트 병합 시 주의
[docs/CONNECTION_AND_INTEGRATION.md](docs/CONNECTION_AND_INTEGRATION.md)에 연결 단계, 역할 인터페이스, 파일 병합 순서, 필수 테스트를 자세히 정리했습니다.

## 4. 파일 구조
```
src/main/java/com/example/bowling/
 ├─ LaneUsageApplication.java                (독립 실행용; 팀 병합 시 추가 금지)
 ├─ controller/{Home,Lane,Usage}Controller.java
 ├─ service/{BusinessException,Lane,Usage}Service.java
 ├─ repository/{Lane,Usage}Repository.java
 └─ dto/{LaneInfo,LaneSchedule,UsageInfo}.java
src/main/resources/
 ├─ application.properties
 ├─ application-demo.properties
 ├─ application-oracle.properties
 ├─ db/h2-{schema,data}.sql                 (데모 전용)
 ├─ templates/lanes/{list,detail}.html
 ├─ templates/usages/list.html
 └─ static/css/style.css
sql/
 ├─ 00_check_oracle.sql                     (읽기 전용)
 ├─ 01_create_usage_sequence_once.sql      (선택: 1회 실행)
 └─ 02_manual_queries.sql                  (읽기 전용)
```

## 5. 추가 완료 작업 (V2)
- **이용 종료의 잠금 순서를 수정했습니다.** 시작/종료 모두 레인 → 예약 → 이용 기록 순으로 잠가 동시 요청 시 교착 위험을 줄였습니다.
- 레인 상세 조회를 전체 레인 조회 대신 해당 레인 단건 조회로 변경했습니다.
- H2 데모 스키마에 예약 2시간/최소 인원, 이용시간 검증 제약조건을 보강했습니다.
- `src/test/java`에 독립 H2 통합 테스트 11개와 화면 요청 테스트 3개를 추가했습니다.
- 연결/테스트/협업 인계 문서: [DB 연결](docs/DB_CONNECTION_WINDOWS.md), [API와 테스트](docs/API_AND_TESTS.md), [팀 전달 문서](docs/HANDOFF_TEAM.md).
- **실제 사용자의 Oracle 접속이나 데이터 변경은 하지 않았습니다.**

## 5. 테스트
1. `/lanes`에서 레인 1~10과 상태 확인
2. `/lanes/1`에서 예약 1001 이용 시작 → `/usages`에서 실제 시작 확인
3. `/usages`에서 이용 종료 → 예약 완료 상태 확인
4. `/lanes/2`에서 예약 1002 시작, `/lanes/3`에서도 시작 → 한 레인 종료 후 예약은 이용중, 둘 다 종료 후 이용완료
5. 레인 9/10의 시작은 거절되어야 함. 등록된 예약 없이 이용 시작 시도는 거절되어야 함.

## 6. 검증의 한계
현재 생성 환경에 Maven이 없고 외부 Maven 저장소 네트워크 연결도 없어 **실제 Spring Boot 구동/Oracle 접속 테스트는 수행하지 못했습니다**. 파일/SQL의 구조적 검사만 수행했습니다. 처음 실행 시 IDE의 Maven 의존성 해결 및 Oracle 기존 CHECK 제약조건 오류를 별도로 점검해야 합니다.

## 7. 보안
- 본 예제는 학습/로컬 시연용이며 로그인·권한 관리·CSRF 검증을 구현하지 않았습니다.
- 학교/공용 Oracle DB에 대해 무단 DDL·초기화 금지.
- 비밀번호는 환경변수로 주입, GitHub 커밋 금지.


### Windows에서 자동 테스트 실행

```powershell
# 프로젝트 폴더에서
mvn test
# 성공 시 데모 모드로 웹 실행
mvn spring-boot:run
```

한글/제약조건이 깨진 기존 개인 Oracle 계정의 데이터를 사용하지 않고 **데모에서 기능부터 검증**하세요. Oracle 연결은 [별도 가이드](docs/DB_CONNECTION_WINDOWS.md)를 따라 진행합니다.
