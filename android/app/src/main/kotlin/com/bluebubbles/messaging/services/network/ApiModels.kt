package com.bluebubbles.messaging.services.network

import com.google.gson.annotations.SerializedName

/**
 * Data models for API requests and responses
 */

// Request models
data class SendMessageRequest(
    @SerializedName("chatGuid")
    val chatGuid: String,
    
    @SerializedName("tempGuid")
    val tempGuid: String,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("method")
    val method: String? = null,
    
    @SerializedName("effectId")
    val effectId: String? = null,
    
    @SerializedName("subject")
    val subject: String? = null,
    
    @SerializedName("selectedMessageGuid")
    val selectedMessageGuid: String? = null,
    
    @SerializedName("partIndex")
    val partIndex: Int? = null,
    
    @SerializedName("ddScan")
    val ddScan: Boolean? = null
)

data class SendTapbackRequest(
    @SerializedName("chatGuid")
    val chatGuid: String,
    
    @SerializedName("selectedMessageText")
    val selectedMessageText: String,
    
    @SerializedName("selectedMessageGuid")
    val selectedMessageGuid: String,
    
    @SerializedName("reaction")
    val reaction: String,
    
    @SerializedName("partIndex")
    val partIndex: Int? = null
)

// Generic API Response
data class ApiResponse(
    @SerializedName("status")
    val status: Int? = null,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("error")
    val error: String? = null,
    
    @SerializedName("data")
    val data: Any? = null
)
