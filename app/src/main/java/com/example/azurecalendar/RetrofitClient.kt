package com.example.azurecalendar

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// ============================================================================
// RETROFIT CLIENT SINGLETON
// ============================================================================

object RetrofitClient {

    // Updated to your computer's local network IP so physical phones can connect to XAMPP
    private const val BASE_URL = "http://192.168.100.15/azure_calendar_backend/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, unit = TimeUnit.SECONDS)
        .readTimeout(15, unit = TimeUnit.SECONDS)
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
