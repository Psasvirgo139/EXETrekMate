package com.trekmate.exe.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Matches Android's TourMemberDto exactly.
 * Note: explicit @JsonProperty on boolean field to prevent Jackson from stripping "is" prefix.
 */
public record MemberDto(

        @JsonProperty("user_id")
        String userId,

        @JsonProperty("is_leader")
        boolean isLeader
) {}
