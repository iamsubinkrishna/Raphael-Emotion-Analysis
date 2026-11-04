package com.example.raphael.api

import com.example.raphael.models.*
import retrofit2.Response
import retrofit2.http.*

interface AnalysisApiService {
    @POST("analysis/analyze_sentiment")
    suspend fun analyzeSentiment(@Body request: SentimentRequest): Response<SentimentResponse>

    @POST("analysis/detect_emotion_image")
    suspend fun detectEmotion(@Body request: EmotionRequest): Response<EmotionResponse>
} 