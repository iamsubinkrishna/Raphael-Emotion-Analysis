package com.example.raphael.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("analysis/detect_emotion")
    fun detectEmotion(@Body request: EmotionDetectionRequest): Call<EmotionDetectionResponse>
} 