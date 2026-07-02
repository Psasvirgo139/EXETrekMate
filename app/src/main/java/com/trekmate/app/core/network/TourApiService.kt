package com.trekmate.app.core.network

import com.trekmate.app.core.network.dto.*
import retrofit2.http.*

interface TourApiService {

    @POST("tours")
    suspend fun createTour(@Body request: CreateTourRequest): CreateTourResponse

    @POST("tours/join")
    suspend fun joinTour(@Body request: JoinTourRequest): JoinTourResponse

    @POST("tours/end")
    suspend fun endTour(@Body request: EndTourRequest): EndTourResponse

    @GET("tours/{tourId}/members")
    suspend fun getMembers(@Path("tourId") tourId: String): MemberListResponse
}
