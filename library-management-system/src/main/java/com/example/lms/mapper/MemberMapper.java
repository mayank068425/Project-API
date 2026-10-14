package com.example.lms.mapper;

import com.example.lms.dto.response.MemberResponse;
import com.example.lms.entity.Member;
import org.springframework.stereotype.Component;

@Component
public class MemberMapper {

    public MemberResponse toResponse(Member member) {
        if (member == null) {
            return null;
        }
        // canBorrow() and isExpired() are computed here rather than stored, so a
        // membership that lapsed yesterday reads as expired today without any
        // job having run.
        return new MemberResponse(member.getId(), member.getMembershipNumber(),
                member.getName(), member.getEmail(), member.getPhone(), member.getAddress(),
                member.getMembershipDate(), member.getMembershipExpiry(),
                member.getMembershipStatus(), member.getBooksOnLoan(),
                member.canBorrow(), member.isExpired());
    }
}
