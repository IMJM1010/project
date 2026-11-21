package db_bowling.service;

import db_bowling.domain.Member;
import db_bowling.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public List<Member> getMembers(String keyword) {
        return memberRepository.findAll(keyword);
    }

    public Member getMember(Long memberId) {
        return memberRepository.findById(memberId);
    }

    public void registerMember(Member member) {
        if (member.getName() == null || member.getName().isBlank()) {
            throw new IllegalArgumentException("회원 이름은 필수입니다.");
        }
        if (member.getPhone() == null || member.getPhone().isBlank()) {
            throw new IllegalArgumentException("연락처는 필수입니다.");
        }
        memberRepository.save(member);
    }

    public void modifyMember(Member member) {
        memberRepository.update(member);
    }

    // 예약 이력이 존재하는 회원은 삭제 차단
    public void removeMember(Long memberId) {
        int resCount = memberRepository.countReservationsByMemberId(memberId);
        if (resCount > 0) {
            throw new IllegalStateException("예약 이력이 존재하는 회원은 삭제할 수 없습니다.");
        }
        memberRepository.deleteById(memberId);
    }
}