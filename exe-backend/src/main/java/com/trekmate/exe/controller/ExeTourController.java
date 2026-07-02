package com.trekmate.exe.controller;

import com.trekmate.exe.dto.request.CreateTourRequest;
import com.trekmate.exe.dto.request.EndTourRequest;
import com.trekmate.exe.dto.request.JoinTourRequest;
import com.trekmate.exe.dto.response.CreateTourResponse;
import com.trekmate.exe.dto.response.EndTourResponse;
import com.trekmate.exe.dto.response.JoinTourResponse;
import com.trekmate.exe.dto.response.MemberListResponse;
import com.trekmate.exe.service.ExeTourService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exe/tours")
@RequiredArgsConstructor
@Tag(name = "TrekMate EXE Tours", description = "Tour execution APIs for the TrekMate Android app")
public class ExeTourController {

    private final ExeTourService tourService;

    @PostMapping
    @Operation(
        summary = "Create a new tour",
        description = "Leader creates a tour session. Returns tourId, groupId (8 chars for BLE), joinCode (6 chars), and qrPayload."
    )
    public ResponseEntity<CreateTourResponse> createTour(@Valid @RequestBody CreateTourRequest request) {
        return ResponseEntity.ok(tourService.createTour(request));
    }

    @PostMapping("/join")
    @Operation(
        summary = "Join an existing tour",
        description = "Member joins tour using a joinCode (typed) or qr scan. Returns full tour info + current member list."
    )
    public ResponseEntity<JoinTourResponse> joinTour(@Valid @RequestBody JoinTourRequest request) {
        return ResponseEntity.ok(tourService.joinTour(request));
    }

    @PostMapping("/end")
    @Operation(
        summary = "End a tour",
        description = "Only the leader can end the tour. Marks the session as inactive."
    )
    public ResponseEntity<EndTourResponse> endTour(@Valid @RequestBody EndTourRequest request) {
        return ResponseEntity.ok(tourService.endTour(request));
    }

    @GetMapping("/{tourId}/members")
    @Operation(
        summary = "Get current member list",
        description = "Returns all members currently in the active tour session."
    )
    public ResponseEntity<MemberListResponse> getMembers(@PathVariable String tourId) {
        return ResponseEntity.ok(tourService.getMembers(tourId));
    }
}
