# 볼링장 예약 및 이용 관리 시스템
> 데이터베이스 및 실습 | 4인 팀 프로젝트 | 개발 준비 단계

## 1. 프로젝트 소개
볼링장 회원의 예약부터 레인 배정, 실제 이용 시작·종료, 운영 통계까지 관리하는 **웹 기반 볼링장 예약 및 이용 관리 시스템**입니다.

기존의 수기 또는 분산 관리 방식 대신, Oracle 관계형 데이터베이스에 회원·예약·레인·이용 기록을 저장하고 웹사이트에서 조회·관리하는 것을 목표로 합니다.

### 주요 목표
- 회원별 예약 내역을 관리하고, 같은 회원이 여러 번 예약할 수 있도록 구현
- 예약 1건에 여러 레인을 배정하고, 레인 1개가 시간대별 여러 예약에 재사용되는 **N:N 관계** 구현
- 레인 시설 상태와 시간대별 예약 충돌을 구분해 검사
- 예약된 레인의 실제 이용 시작/종료 시간 기록
- JOIN·GROUP BY 등 SQL을 활용한 운영 통계 제공

## 2. 주요 기능 및 화면
| 메뉴 | 기능 | 담당 |
|---|---|---|
| 회원 관리 | 회원 목록·상세·등록·수정, 회원별 예약 내역 | 1번 |
| 예약 관리 | 예약 생성·조회·취소, 레인 배정, 시간 중복 검사 | 2번 |
| 레인 및 이용 관리 | 레인 현황·시설 상태, 이용 시작·종료, 이용 기록 | 3번 |
| 대시보드·통계 | 회원/예약/레인 현황, 회원별 예약 수, 레인별 이용 수 | 4번 |

※ 로그인/권한, 결제, 볼링화 대여는 현재 범위에서 제외하고 향후 확장 대상으로 둡니다.

## 3. DB 설계: 5개 테이블
| 테이블 | 주요 내용 |
|---|---|
| `MEMBER` | member_id(PK), name, phone, gender, join_date, member_grade |
| `LANE` | lane_id(PK), lane_number(UNIQUE), lane_status |
| `RESERVATION` | reservation_id(PK), member_id(FK), start_time, end_time, person_count, reservation_status |
| `RESERVATION_LANE` | reservation_id(FK), lane_id(FK), 복합 PK |
| `USAGE` | usage_id(PK), reservation_id+lane_id(복합 FK), actual_start_time, actual_end_time, usage_status |

### 관계
- `MEMBER 1:N RESERVATION`
- `RESERVATION 1:N RESERVATION_LANE`
- `LANE 1:N RESERVATION_LANE`
- **개념적으로 `RESERVATION N:N LANE`**
- `RESERVATION_LANE 1:0..1 USAGE` (예약-레인 한 쌍당 이용 기록 최대 1개)

### 핵심 업무 규칙
1. 예약 1건의 이용시간은 **최대 2시간**.
2. 레인 1개당 최대 **4명**; 필요한 레인 수는 `ceil(예약인원/4)` 이상.
3. `점검중`/ `사용불가` 시설 상태의 레인은 예약 불가.
4. 같은 레인의 **겹치는 시간대**에 유효 예약을 중복 배정할 수 없음. 취소 예약은 점유에서 제외.
5. 예약 생성과 레인 배정은 함께 성공하거나 함께 실패해야 함(트랜잭션).
6. 실제 이용시간은 `USAGE`에서 관리하며, 시설 상태(`LANE.lane_status`)와 예약/이용 상태를 혼동하지 않음.

## 4. 개발 기술 (예정)
- **DB:** Oracle 19c, SQL
- **백엔드:** Java 17+, Spring Boot, Spring JDBC
- **프론트엔드:** Thymeleaf, HTML, CSS, JavaScript (필요 시 Bootstrap)
- **개발 환경:** IntelliJ IDEA 또는 VS Code
- **협업:** GitHub 브랜치 + Pull Request

현재 단계에서는 **코드를 넣지 않았으며 역할 및 설계 문서만 작성**했습니다. 실제 구현 방식과 라이브러리 버전은 팀 회의 후 확정합니다.

## 5. 4인 역할별 업무 안내
| 담당 | 역할 폴더 | 구현 책임 |
|---|---|---|
| **1번** | [DB·서버·회원 관리](roles/01-db-member/README.md) | 공통 프로젝트/Oracle 연결 + 회원 기능 |
| **2번** | [예약 관리](roles/02-reservation/README.md) | 예약 CRUD + 다중 레인 배정 |
| **3번** | [레인·이용 기록](roles/03-lane-usage/README.md) | 레인 현황 + 이용 시작/종료 |
| **4번** | [UI·대시보드·통계](roles/04-dashboard-ui/README.md) | 공통 디자인 + 운영 통계 |

공통 협업 규칙: [docs/TEAM_GUIDE.md](docs/TEAM_GUIDE.md)

## 6. 개발 우선순위
1. **환경 준비:** 프로젝트 초기화, Oracle 연결, 공통 화면 골격 및 URL 합의
2. **핵심 조회:** 회원·예약·레인 목록 조회
3. **핵심 변경:** 예약 생성/취소 → 레인 배정 → 이용 시작/종료
4. **통계 및 통합:** JOIN/집계 SQL, 대시보드, 예외 처리
5. **검증/발표:** 예약 6명→레인 2개 배정, 시간 충돌 거절, DB 반영 시연

## 7. 완료 기준
- 웹사이트와 실제 Oracle 테이블 사이에 조회/저장/수정이 반영됨
- 회원 1:N 예약, 예약 N:N 레인, 이용 기록 관계를 SQL로 설명하고 시연할 수 있음
- 예약 충돌, 인원 제한, 잘못된 상태 전환을 차단할 수 있음
- 4명이 동일한 개발 환경에서 실행 가능하며 역할별 기능이 통합됨

> **보안 주의:** Oracle 계정·비밀번호, 개인 연락처, 서버 인증 정보 및 실제 사용자 데이터는 저장소에 올리지 않습니다. 개발용 데이터는 가상 데이터만 사용합니다.
