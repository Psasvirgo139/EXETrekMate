package com.trekmate.exe.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response for POST /exe/tours/join.
 * Matches Android's JoinTourResponse DTO field names.
 */
public record JoinTourResponse(

        @JsonProperty("tour_id")
        String tourId,

        @JsonProperty("group_id")
        String groupId,

        @JsonProperty("leader_id")
        String leaderId,

        @JsonProperty("join_code")
        String joinCode,

        @JsonProperty("qr_payload")
        String qrPayload,

        @JsonProperty("members")
        List<MemberDto> members
) {}
