package com.trekmate.exe.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Android sends: { "leader_id": "16charHexString" }
 */
public record CreateTourRequest(

        @JsonProperty("leader_id")
        @NotBlank(message = "leader_id is required")
        @Size(max = 16, message = "leader_id must be at most 16 characters")
        String leaderId
) {}
