package com.bluebubbles.messaging.services.network

import retrofit2.Call
import retrofit2.http.*

/**
 * Retrofit API interface for BlueBubbles server endpoints
 */
interface BlueBubblesApi {
    
    /**
     * Send a text message
     */
    @POST("message/text")
    fun sendMessage(
        @Query("guid") guid: String,
        @Body request: SendMessageRequest
    ): Call<ApiResponse>
    
    /**
     * Send a reaction/tapback
     */
    @POST("message/react")
    fun sendTapback(
        @Query("guid") guid: String,
        @Body request: SendTapbackRequest
    ): Call<ApiResponse>
    
    /**
     * Ping the server
     */
    @GET("ping")
    fun ping(
        @Query("guid") guid: String
    ): Call<ApiResponse>
}
