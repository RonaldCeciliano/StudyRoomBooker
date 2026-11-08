package com.studyroombooker.backend.controller;

import java.security.Principal;

import com.studyroombooker.backend.service.ShiftBookingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShiftBookingController {
    private final ShiftBookingService bookings;

    public ShiftBookingController(ShiftBookingService bookings) { this.bookings = bookings; }

    public record MarkBookedRequest(Long bookedRoomId) {}

    @PostMapping("/api/shifts/{id}/booked")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markBooked(@PathVariable long id, @RequestBody(required = false) MarkBookedRequest request,
                           Principal principal) {
        bookings.markBooked(id, principal == null ? null : principal.getName(),
            request == null ? null : request.bookedRoomId());
    }
}
