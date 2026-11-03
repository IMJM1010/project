# 3번 담당 구현 현황 — 2026-11-03

## 담당
- 레인 관리: 전체 레인 조회, 시설 상태 변경, 예약 일정 조회
- 실제 이용 관리: 이용 시작, 이용 종료, 이력 조회, 다중 레인 이용
- 기존 테이블: `LANE`, `USAGE` 직접 관리; `RESERVATION`, `RESERVATION_LANE`, `MEMBER` 조회

## 진행 결과
- [x] 3번 담당용 독립 Spring Boot 프로젝트 코드 작성
- [x] `LaneController`, `LaneService`, `LaneRepository`
- [x] `UsageController`, `UsageService`, `UsageRepository`
- [x] Thymeleaf 레인 목록·상세, 이용 기록 화면
- [x] 개인 Oracle 연결용 설정 (환경변수 기반; 자동 스키마 초기화 없음)
- [x] Oracle 없이 실행할 수 있는 H2 데모 모드와 테스트 예약
- [x] 이용 시작/종료의 잠금 순서를 레인 → 예약 → 이용 기록 순서로 일치시킴
- [x] H2 통합 테스트 11개, 웹 페이지 요청 테스트 3개 작성
- [x] 1·2·4번 담당자와 연동하기 위한 체크리스트
- [ ] 로컬 컴퓨터에서 `mvn test` 성공 확인
- [ ] 로컬 컴퓨터에서 `mvn spring-boot:run` 브라우저 화면 확인
- [ ] 개인 Oracle 19c에 네트워크/JDBC 접속 확인
- [ ] 개인 Oracle의 `CHECK` 제약조건과 USAGE_SEQ 확인
- [ ] 2번 예약 담당자와 예약 상태 규칙 최종 합의
- [ ] 4번 담당자 공통 UI 스타일 적용
- [ ] 팀 공통 Spring Boot 프로젝트에 소스 통합

## 유의 사항
1. **이번 브랜치에는 진행 문서와 연동 규칙을 올렸습니다. 완성된 독립 예제 소스는 ChatGPT 대화에 제공한 ZIP에 있습니다.** 팀 공통 프로젝트 구조가 확정되기 전까지 덮어쓰지 않습니다.
2. 컴파일 검증은 핵심 서비스 Java 문법 검사까지만 수행했습니다. Maven 실행/자동 테스트 14개는 아직 미실행이며, 성공으로 표시해서는 안 됩니다.
3. 개인 Oracle 사용자 계정 또는 원격 DB에 직접 접속/수정한 적은 없습니다.
4. `USAGE`의 `usage_id`는 자동증가가 아니므로 `USAGE_SEQ` 정책이 필요합니다.
5. 다른 팀원의 예약 등록 코드는 필요 없지만, 이용 시작 테스트에는 이미 배정된 예약이 필요합니다. 독립 H2 데모에 해당 예약이 포함되어 있습니다.

## 다음 단계
1. IntelliJ/VS Code에서 ZIP 프로젝트 실행, `mvn test` 검증
2. `http://localhost:8080/lanes`에서 10개 레인 확인
3. 1001 레인 1번 시작/종료, 1002 다중 레인 시작/종료
4. [Oracle 연결](docs/ORACLE_CONNECTION.md) 점검
5. [팀 인터페이스](docs/TEAM_HANDOFF.md) 합의
6. [테스트 목록](docs/TEST_CASES.md) 증빙 준비
