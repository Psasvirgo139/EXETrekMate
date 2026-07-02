package com.trekmate.exe.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Android sends: { "user_id": "16charHexString", "join_code": "6CHARS" }
 */
public record JoinTourRequest(

        @JsonProperty("user_id")
        @NotBlank(message = "user_id is required")
        @Size(max = 16, message = "user_id must be at most 16 characters")
        String userId,

        @JsonProperty("join_code")
        @NotBlank(message = "join_code is required")
        String joinCode
) {}
