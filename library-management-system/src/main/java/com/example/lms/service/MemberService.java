package com.example.lms.service;

import com.example.lms.dto.request.MemberRequest;
import com.example.lms.dto.response.MemberResponse;
import com.example.lms.entity.Member;
import com.example.lms.entity.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberService {

    MemberResponse register(MemberRequest request);

    /** Creates the member record for a self-registering user, in one transaction. */
    Member registerForSelfSignup(String name, String email, String phone, String address);

    MemberResponse update(Long id, MemberRequest request);

    void deactivate(Long id, String reason);

    MemberResponse setStatus(Long id, MembershipStatus status, String reason);

    MemberResponse get(Long id);

    Page<MemberResponse> search(String query, MembershipStatus status, Pageable pageable);

    Member requireMember(Long id);

    String nextMembershipNumber();
}
