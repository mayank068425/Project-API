package com.example.lms.service;

import com.example.lms.dto.request.ReserveBookRequest;
import com.example.lms.dto.response.ReservationResponse;

import java.util.List;

public interface ReservationService {

    ReservationResponse reserve(ReserveBookRequest request);

    void cancel(Long reservationId);

    List<ReservationResponse> myReservations();

    List<ReservationResponse> queueForBook(Long bookId);

    /**
     * Sweeps lapsed holds and aged-out reservations.
     *
     * <p>Scheduled, but nothing depends on it having run: a lapsed hold is also
     * detected on the next issue attempt. A queue whose correctness rests on a
     * cron job is wrong whenever the job fails.
     */
    int expireLapsed();
}
