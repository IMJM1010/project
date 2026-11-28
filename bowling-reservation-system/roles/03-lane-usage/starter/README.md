# 볼링장 3번 담당: 레인·이용 관리 (v1.5 · 독립 실행용)

## 개발 환경
- JDK 21, Spring Boot 4.2.0, Maven, Spring JDBC, Spring Security, Thymeleaf, Oracle JDBC (ojdbc17), H2
- 회원/예약 담당 코드와 통합 전의 독립 실행 프로젝트입니다. 상세 기준: [v1.5 명세 반영 및 통합 주의사항](docs/V15_MIGRATION.md).
- 이전 문서의 Java 17, Boot 3.5.7, 한글 DB 상태값 및 USAGE_SEQ 안내는 이전 버전 기준이며 최신 코드에 적용하지 않습니다.

## 로컬 H2 데모 실행 (PowerShell)
```powershell
java -version
javac -version
mvn -version
mvn -U clean compile
mvn clean test
$env:SPRING_PROFILES_ACTIVE="demo"
mvn spring-boot:run
```

java, javac, Maven의 Java version이 모두 21인지 확인합니다. release version 21 not supported 오류는 Maven이 사용하는 JDK를 점검합니다.

- 접속: http://localhost:8080/lanes
- 로컬 데모 계정: admin / local-demo-only (DEMO_ADMIN_PASSWORD 환경변수로 변경 가능)
- 데모는 루프백 주소에만 바인딩되며 서버 재시작 시 메모리 DB가 초기화됩니다.
- 예약 1001: 1번 레인, 3명. 예약 1002: 2·3번 레인, 6명. 예약 1003: 완료 기록.
- 9·10번 레인은 점검중 상태입니다.

## 주요 기능
- 레인 조회, 고장/점검 처리, 대체 레인 재배정 및 실패 표시, 점검 완료
- 실제 방문 인원 확인, 개별 이용 시작/종료, 미사용, 전체 종료, 이용시간 연장, 추가 레인 배정
- RESERVATION_LANE 독립 PK 및 USAGE의 배정 ID FK, 영문 상태값
- Spring Security 관리자 권한, CSRF, Spring JDBC 트랜잭션 및 공통 레인 잠금
- 팀 공통 잠금 규칙, 인증, 영업 마감 및 재배정실패 복구 정책은 통합 전 협의가 필요합니다.

## Oracle 19c 연결
Oracle 초기화는 자동 실행되지 않습니다. 먼저 sql/00_check_oracle.sql로 읽기 전용 점검을 진행합니다. sql/03_v15_schema_fresh_only.sql은 별도 빈 테스트 스키마 전용이며 기존 데이터의 마이그레이션 스크립트가 아닙니다. IDENTITY는 팀 합의 전의 제안입니다.

SSH 터널을 사용하는 경우 학교 정책에 따라 허용된 터널을 먼저 연결합니다.

```powershell
Test-NetConnection 127.0.0.1 -Port 11521
$env:SPRING_PROFILES_ACTIVE="oracle"
$env:DB_URL="jdbc:oracle:thin:@//127.0.0.1:11521/orcl"
$env:DB_USERNAME="<ORACLE_USER>"
$env:DB_PASSWORD="<ORACLE_PASSWORD>"
mvn spring-boot:run
```

Oracle 로그인에는 활성 상태의 ADMIN 회원 및 BCrypt 비밀번호 해시가 필요합니다. DB 비밀번호는 Git에 커밋하지 않습니다. 기존 DB 담당자의 스키마가 v1.5와 일치하지 않으면 연결 후 SQL 오류가 발생할 수 있습니다.

## 검증 및 통합
- H2 테스트와 Oracle 테스트는 별도로 수행합니다.
- 이 업그레이드 커밋은 편집 환경에서 Maven 빌드 및 실제 DB 테스트를 실행하지 않았습니다.
- 기존 문서보다 [V15_MIGRATION.md](docs/V15_MIGRATION.md)를 우선합니다.
- 팀 스키마와 인터페이스를 합의하기 전 main으로 병합하지 않습니다.
