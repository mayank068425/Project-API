package com.example.lms.controller;

import com.example.lms.dto.request.ReserveBookRequest;
import com.example.lms.dto.response.ApiResponse;
import com.example.lms.dto.response.ReservationResponse;
import com.example.lms.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @Operation(summary = "Join the queue for a title that is fully out on loan",
            description = "Refused if copies are on the shelf: borrow it instead. A "
                    + "returned copy is held for the first member in the queue rather "
                    + "than shelved, so the queue actually drains.")
    public ResponseEntity<ApiResponse<ReservationResponse>> reserve(
            @Valid @RequestBody ReserveBookRequest request) {
        ReservationResponse reservation = reservationService.reserve(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(reservation,
                "You are number " + reservation.queuePosition() + " in the queue."));
    }

    @GetMapping("/me")
    public ApiResponse<List<ReservationResponse>> mine() {
        return ApiResponse.ok(reservationService.myReservations());
    }

    @GetMapping("/book/{bookId}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public ApiResponse<List<ReservationResponse>> queue(@PathVariable Long bookId) {
        return ApiResponse.ok(reservationService.queueForBook(bookId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a reservation",
            description = "Cancelling one that is already READY frees the held copy, "
                    + "which passes to the next member in the queue.")
    public ApiResponse<String> cancel(@PathVariable Long id) {
        reservationService.cancel(id);
        return ApiResponse.ok("Reservation cancelled");
    }
}
