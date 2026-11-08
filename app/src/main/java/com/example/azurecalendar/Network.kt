package com.example.azurecalendar

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// ============================================================================
// DATA TRANSFER OBJECTS (DTOs)
// ============================================================================

data class UserDto(
    val id: Int,
    val name: String,
    val email: String,
    val role: String // "Agent", "Admin", or "Owner"
)

data class ApiResponse<T>(
    val status: String,
    val message: String?,
    val data: T
)

data class LoginRequest(
    val emailOrUsername: String,
    val password: String
)

data class RegisterRequest(
    val fullName: String,
    val email: String,
    val password: String,
    val age: Int,
    val gender: String,
    val position: String // "Agent" or "Admin"
)

// ============================================================================
// RETROFIT API SERVICE INTERFACE
// ============================================================================

interface AzureApiService {

    @GET("api/get_users.php")
    suspend fun getAllUsers(): Response<ApiResponse<List<UserDto>>>

    @POST("api/login.php")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<UserDto>>

    @POST("api/register.php")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<ApiResponse<String>>
}