package com.example.lms.dto.response;

import com.example.lms.entity.MembershipStatus;

import java.time.LocalDate;

public record MemberResponse(Long id, String membershipNumber, String name, String email,
                             String phone, String address, LocalDate membershipDate,
                             LocalDate membershipExpiry, MembershipStatus membershipStatus,
                             int booksOnLoan, boolean canBorrow, boolean expired) {
}
