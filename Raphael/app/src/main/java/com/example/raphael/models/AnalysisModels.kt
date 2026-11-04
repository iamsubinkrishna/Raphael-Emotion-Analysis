package com.example.raphael.models

data class SentimentRequest(
    val text: String
)

data class EmotionRequest(
    val image: String  // Base64 encoded image
)

data class SentimentResponse(
    val status: String,
    val data: SentimentData?,
    val message: String
)

data class SentimentData(
    val sentiment: String,
    val polarity: Double,
    val subjectivity: Double,
    val text: String
)

data class EmotionResponse(
    val status: String,
    val data: EmotionData?,
    val message: String
)

data class EmotionData(
    val emotions: Map<String, Double>
) 