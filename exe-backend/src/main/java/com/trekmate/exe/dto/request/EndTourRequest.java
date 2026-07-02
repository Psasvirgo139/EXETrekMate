package com.trekmate.exe.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Android sends: { "tour_id": "...", "leader_id": "16charHexString" }
 */
public record EndTourRequest(

        @JsonProperty("tour_id")
        @NotBlank(message = "tour_id is required")
        String tourId,

        @JsonProperty("leader_id")
        @NotBlank(message = "leader_id is required")
        String leaderId
) {}
