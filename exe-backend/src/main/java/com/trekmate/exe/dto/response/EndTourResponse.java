package com.trekmate.exe.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for POST /exe/tours/end.
 */
public record EndTourResponse(

        @JsonProperty("success")
        boolean success,

        @JsonProperty("message")
        String message
) {}
