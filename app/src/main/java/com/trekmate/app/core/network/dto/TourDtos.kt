package com.trekmate.app.core.network.dto

import com.google.gson.annotations.SerializedName

data class CreateTourRequest(
    @SerializedName("leader_id") val leaderId: String
)

data class CreateTourResponse(
    @SerializedName("tour_id") val tourId: String,
    @SerializedName("group_id") val groupId: String,
    @SerializedName("join_code") val joinCode: String,
    @SerializedName("qr_payload") val qrPayload: String,
    @SerializedName("leader_id") val leaderId: String
)

data class JoinTourRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("join_code") val joinCode: String
)

data class JoinTourResponse(
    @SerializedName("tour_id") val tourId: String,
    @SerializedName("group_id") val groupId: String,
    @SerializedName("leader_id") val leaderId: String,
    @SerializedName("join_code") val joinCode: String,
    @SerializedName("qr_payload") val qrPayload: String,
    @SerializedName("members") val members: List<TourMemberDto>
)

data class EndTourRequest(
    @SerializedName("tour_id") val tourId: String,
    @SerializedName("leader_id") val leaderId: String
)

data class EndTourResponse(
    @SerializedName("success") val success: Boolean
)

data class TourMemberDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("is_leader") val isLeader: Boolean
)

data class MemberListResponse(
    @SerializedName("members") val members: List<TourMemberDto>
)
