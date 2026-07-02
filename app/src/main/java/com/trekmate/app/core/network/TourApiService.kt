package com.trekmate.app.core.network

import com.trekmate.app.core.network.dto.*
import retrofit2.http.*

interface TourApiService {

    @POST("exe/tours")
    suspend fun createTour(@Body request: CreateTourRequest): CreateTourResponse

    @POST("exe/tours/join")
    suspend fun joinTour(@Body request: JoinTourRequest): JoinTourResponse

    @POST("exe/tours/end")
    suspend fun endTour(@Body request: EndTourRequest): EndTourResponse

    @GET("exe/tours/{tourId}/members")
    suspend fun getMembers(@Path("tourId") tourId: String): MemberListResponse
}
