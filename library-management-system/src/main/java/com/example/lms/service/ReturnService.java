package com.example.lms.service;

import com.example.lms.dto.request.ReturnBookRequest;
import com.example.lms.dto.response.ReturnResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReturnService {

    /**
     * Takes a copy back, assesses any fine, and decides where the copy goes.
     *
     * <p>That last part is the one that is easy to get wrong: with members queued
     * for the title, the copy is held for the first of them rather than shelved.
     */
    ReturnResponse returnBook(ReturnBookRequest request);

    Page<ReturnResponse> list(Long memberId, Pageable pageable);

    ReturnResponse getByReference(String reference);
}
