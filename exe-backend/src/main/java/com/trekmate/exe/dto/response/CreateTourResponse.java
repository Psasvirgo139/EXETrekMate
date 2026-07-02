package com.trekmate.exe.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for POST /exe/tours.
 * Matches Android's CreateTourResponse DTO field names.
 */
public record CreateTourResponse(

        @JsonProperty("tour_id")
        String tourId,

        @JsonProperty("group_id")
        String groupId,

        @JsonProperty("join_code")
        String joinCode,

        @JsonProperty("qr_payload")
        String qrPayload,

        @JsonProperty("leader_id")
        String leaderId
) {}
