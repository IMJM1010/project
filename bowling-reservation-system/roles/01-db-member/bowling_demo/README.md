# 01-db-member (DB 초기 스키마 및 회원 관리)

## 1. 담당 범위
- **전체 DB 스키마 및 더미데이터 생성 (`sql/01_schema_and_dummy.sql`)**
  - 시퀀스 4개 (`SEQ_MEMBER_ID`, `SEQ_LANE_ID`, `SEQ_RESERVATION_ID`, `SEQ_USAGE_ID`)
  - 테이블 5개 (`MEMBER`, `LANE`, `RESERVATION`, `RESERVATION_LANE`, `USAGE`)
  - 무결성 정책: `ON DELETE CASCADE` 미사용 (예약 이력이 존재하는 회원은 삭제 차단)
- **회원(MEMBER) CRUD 및 검색 기능 구현**

## 2. 환경변수 설정 (`application.properties`)
보안을 위해 실제 DB 계정 정보는 하드코딩하지 않고 환경변수로 관리합니다.
- `DB_URL`: `jdbc:oracle:thin:@localhost:1521/XEPDB1`
- `DB_USERNAME`: Oracle 사용자명
- `DB_PASSWORD`: Oracle 비밀번호

## 3. 주요 엔드포인트 (UI 연동용)
| HTTP | URL | 설명 | View |
| :--- | :--- | :--- | :--- |
| `GET` | `/members` | 회원 목록 조회 및 이름/연락처 검색 (`?keyword=`) | `members/list` |
| `GET` | `/members/new` | 신규 회원 등록 폼 | `members/form` |
| `POST` | `/members` | 신규 회원 등록 처리 | `redirect:/members` |
| `GET` | `/members/{id}` | 회원 상세 조회 및 수정 폼 | `members/detail` |
| `POST` | `/members/{id}/edit` | 회원 정보 수정 처리 | `redirect:/members/{id}` |
| `POST` | `/members/{id}/delete` | 회원 삭제 처리 (예약 이력 존재 시 차단) | `redirect:/members` |