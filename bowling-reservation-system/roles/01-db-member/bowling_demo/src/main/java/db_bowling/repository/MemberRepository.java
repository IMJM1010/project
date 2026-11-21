package db_bowling.repository;

import db_bowling.domain.Member;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public MemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Member> memberRowMapper = (rs, rowNum) -> {
        Member m = new Member();
        m.setMemberId(rs.getLong("MEMBER_ID"));
        m.setName(rs.getString("NAME"));
        m.setPhone(rs.getString("PHONE"));
        m.setEmail(rs.getString("EMAIL"));
        m.setGrade(rs.getString("GRADE"));
        m.setCreatedAt(rs.getDate("CREATED_AT"));
        return m;
    };

    // 회원 전체 목록 및 이름/연락처 검색
    public List<Member> findAll(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            String sql = "SELECT MEMBER_ID, NAME, PHONE, EMAIL, GRADE, CREATED_AT FROM MEMBER ORDER BY MEMBER_ID DESC";
            return jdbcTemplate.query(sql, memberRowMapper);
        }
        String sql = "SELECT MEMBER_ID, NAME, PHONE, EMAIL, GRADE, CREATED_AT FROM MEMBER " +
                     "WHERE NAME LIKE ? OR PHONE LIKE ? ORDER BY MEMBER_ID DESC";
        String param = "%" + keyword.trim() + "%";
        return jdbcTemplate.query(sql, memberRowMapper, param, param);
    }

    // 회원 단건 상세 조회
    public Member findById(Long memberId) {
        String sql = "SELECT MEMBER_ID, NAME, PHONE, EMAIL, GRADE, CREATED_AT FROM MEMBER WHERE MEMBER_ID = ?";
        return jdbcTemplate.queryForObject(sql, memberRowMapper, memberId);
    }

    // 회원 등록
    public void save(Member member) {
        String sql = "INSERT INTO MEMBER (MEMBER_ID, NAME, PHONE, EMAIL, GRADE, CREATED_AT) " +
                     "VALUES (SEQ_MEMBER_ID.NEXTVAL, ?, ?, ?, ?, SYSDATE)";
        String grade = (member.getGrade() == null || member.getGrade().isBlank()) ? "NORMAL" : member.getGrade();
        jdbcTemplate.update(sql, member.getName(), member.getPhone(), member.getEmail(), grade);
    }

    // 회원 수정
    public void update(Member member) {
        String sql = "UPDATE MEMBER SET NAME = ?, PHONE = ?, EMAIL = ?, GRADE = ? WHERE MEMBER_ID = ?";
        jdbcTemplate.update(sql, member.getName(), member.getPhone(), member.getEmail(), member.getGrade(), member.getMemberId());
    }

    // 예약 이력 건수 확인 (ON DELETE CASCADE 미사용 -> 삭제 차단 판단용)
    public int countReservationsByMemberId(Long memberId) {
        String sql = "SELECT COUNT(*) FROM RESERVATION WHERE MEMBER_ID = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, memberId);
        return count != null ? count : 0;
    }

    // 회원 삭제
    public void deleteById(Long memberId) {
        String sql = "DELETE FROM MEMBER WHERE MEMBER_ID = ?";
        jdbcTemplate.update(sql, memberId);
    }
}