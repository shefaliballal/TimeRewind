package com.example.timerewind
import com.example.timerewind.TranscriptionResponse

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    // POST request to /transcribe for audio transcription
    @Multipart
    @POST("/transcribe")
    fun transcribeAudio(
        @Part audio: MultipartBody.Part
    ): Call<TranscriptionResponse>

    // POST request to /search_memory for querying stored memories
    @POST("/search_memory")
    fun searchMemory(
        @Body query: HashMap<String, String>
    ): Call<List<MemoryItem>>

    // POST request to /save_transcript for saving transcripts after transcription
    @POST("/save_transcript")
    fun saveTranscript(
        @Body body: HashMap<String, String>
    ): Call<Any>
}
