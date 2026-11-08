package com.example.azurecalendar

import retrofit2.Response
import retrofit2.http.GET

interface ApiService {
    @GET("get_users.php") // Adjust this endpoint to match your XAMPP server script name
    suspend fun getAllUsers(): Response<UserApiResponse>
}

// Data models matching your XAMPP JSON response structure
data class UserApiResponse(
    val data: List<UserModel>?
)

data class UserModel(
    val name: String?,
    val role: String?,
    val email: String?
)