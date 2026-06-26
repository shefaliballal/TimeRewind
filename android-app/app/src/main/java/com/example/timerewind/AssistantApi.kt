package com.example.timerewind

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AssistantApi {
    @POST("/assistant")
    fun askQuestion(@Body request: AssistantRequest): Call<AssistantResponse>
}

data class AssistantRequest(
    val question: String,
    val context: String
)

data class AssistantResponse(
    val reply: String
) 