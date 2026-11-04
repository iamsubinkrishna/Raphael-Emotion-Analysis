package com.example.raphael.models

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String?,
    val username: String?,
    val message: String
)

data class UserResponse(
    val username: String,
    val email: String
) 