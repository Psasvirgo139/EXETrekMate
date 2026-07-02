package com.trekmate.exe.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response for GET /exe/tours/{tourId}/members.
 */
public record MemberListResponse(

        @JsonProperty("members")
        List<MemberDto> members
) {}
