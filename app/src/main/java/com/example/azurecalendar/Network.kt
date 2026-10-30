package com.example.azurecalendar

import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

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

// ============================================================================
// RETROFIT CLIENT SINGLETON
// ============================================================================

object RetrofitClient {

    // 10.0.2.2 routes directly to your host machine's localhost (e.g., XAMPP Apache) from Android Emulator
    private const val BASE_URL = "http://10.0.2.2/azure_calendar_backend/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val api: AzureApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AzureApiService::class.java)
    }
}