# 3번 담당 - Windows Spring Boot ↔ 개인 Oracle 19c 연결 가이드

## 0. 현재 확인된 구조

Windows (IntelliJ/VS Code) → PuTTY SSH → Ubuntu shell → `sqlplus 개인계정@DB주소:1521/orcl` → Oracle 19c.

SQL*Plus에서 접속된다고 Windows Spring Boot에서 **바로** Oracle 연결이 되는 것은 아닙니다. Windows와 DB 사이의 네트워크 경로가 필요합니다. SSH 서버와 DB 서버는 서로 다른 호스트일 수 있습니다.

## 1. Windows 직접 접속 시도 (허용된 네트워크에서)

Windows PowerShell에서 (`<...>`를 실제 주소로 변경):

```powershell
Test-NetConnection <DB_HOST> -Port 1521
```

- `TcpTestSucceeded: True`: 1521 TCP 도달성만 확인됨. Oracle 인증 성공 의미 아님.
- `False`: 방화벽/네트워크 정책, 주소/포트, 접근 권한을 확인. **무단 우회 금지**.

### 직접 접속 환경변수

```powershell
$env:SPRING_PROFILES_ACTIVE="oracle"
$env:DB_URL="jdbc:oracle:thin:@//<DB_HOST>:1521/orcl"
$env:DB_USERNAME="<ORACLE_USER>"
$env:DB_PASSWORD="<ORACLE_PASSWORD>"
mvn spring-boot:run
```

비밀번호를 GitHub의 소스나 설정 파일에 작성하지 마세요.

## 2. SSH 터널 (학교가 포트 포워딩을 허용하는 경우에만)

PuTTY → 기존 SSH 세션 불러오기 → `Connection > SSH > Tunnels`:

- `Source port`: `11521` (로컬 포트)
- `Destination`: `<DB_HOST>:1521` (**Oracle 서버 호스트**, SSH Ubuntu 호스트가 아닐 수 있음)
- `Local` 선택 → Add
- 로컬 인터페이스만 바인딩. **Remote/Local ports accept connections from other hosts** 활성화하지 말 것
- SSH 세션을 새로 연 뒤 Ubuntu 로그인 상태로 유지

Windows PowerShell:

```powershell
Test-NetConnection 127.0.0.1 -Port 11521
$env:SPRING_PROFILES_ACTIVE="oracle"
$env:DB_URL="jdbc:oracle:thin:@//127.0.0.1:11521/orcl"
$env:DB_USERNAME="<ORACLE_USER>"
$env:DB_PASSWORD="<ORACLE_PASSWORD>"
mvn spring-boot:run
```

PuTTY가 종료되면 터널도 종료됩니다. 터널이 열려도 DB 서버 주소/접근 정책 때문에 연결이 실패할 수 있습니다.

## 3. Oracle 접속 전에 개인 계정 SQL 조회

기존 방식으로 SQL*Plus에 접속한 후 아래 파일 내용 참고:

- `sql/00_check_oracle.sql` - 5개 테이블, 건수, CHECK 조건, 시퀀스 확인(읽기 전용)
- `sql/02_manual_queries.sql` - 레인/예약 배정/이용 기록 조회(읽기 전용)

과거에 다음 오류가 있었음:
- `CK_LANE_STATUS` 오류: 데이터에 넣으려는 상태값과 기존 테이블 CHECK 조건 불일치 가능성
- `ORA-12704`: `UNISTR`/문자 집합 처리 문제

**기존 제약조건을 함부로 DROP하지 마세요.** Java에서 한글 상태값을 보내도 DB의 CHECK가 잘못 저장돼 있으면 업데이트가 실패합니다. 먼저 `ASCIISTR(search_condition_vc)` 출력으로 확인하세요.

## 4. Oracle에 레인 데이터가 없을 때

웹사이트 Oracle 모드에서는 테이블이나 데이터를 **자동 생성하지 않음**. LANE 테이블이 비어 있다면 `/lanes`가 빈 화면으로 표시됩니다. 이 경우 1번 담당과 협의해 정상적으로 허용된 상태값을 가진 가상 레인 데이터를 개인 테스트 계정에 입력해야 합니다.

## 5. 이용 기록 ID(Sequence)

기존 테이블의 `USAGE.usage_id`는 IDENTITY가 아니므로, 새로운 이용 기록에는 `USAGE_SEQ.NEXTVAL` 사용.

```sql
SELECT MAX(usage_id) FROM USAGE;
SELECT sequence_name FROM user_sequences WHERE sequence_name='USAGE_SEQ';
```

없고 ID 최대값이 100000 미만일 때만 (`CREATE SEQUENCE` 권한 필요):

```sql
CREATE SEQUENCE USAGE_SEQ START WITH 100000 INCREMENT BY 1 NOCACHE;
```

실제 기존 ID와 충돌하지 않게 시작값 결정. 자동으로 생성하지 않음.

## 6. 연결 실패 시 체크

| 증상 | 점검 지점 |
|---|---|
| `Connection refused` | 로컬 터널 미실행/DB 포트 틀림 |
| `Connection timed out` | 네트워크 정책·주소·SSH 포워딩 허용 여부 |
| `ORA-01017` | Oracle 사용자명 또는 비밀번호 오류 |
| `ORA-12514` | 서비스명 `orcl` 확인 |
| `ORA-00942` | 로그인 스키마에 테이블 없음 / 권한 문제 |
| `ORA-02290` | CHECK 제약조건과 상태 값 불일치 |
| `ORA-02289` | `USAGE_SEQ` 미생성 |

## 7. 구현/전달 결과

1번 담당자에게는 **접속 성공 여부**, `spring.datasource` 환경변수명, Oracle 5개 테이블 조회 성공 여부만 공유합니다. 실제 비밀번호와 SSH 키는 절대 전달하거나 커밋하지 않습니다.
