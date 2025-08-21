package com.example.franwan.network

import com.example.franwan.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {
    private val logging: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val uaRequest = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "FranWan-Android/1.0"
                    )
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(uaRequest)
            }
            .addInterceptor(logging)
            .build()
    }

    val api: ApiService by lazy {
        val gson = com.google.gson.GsonBuilder()
            .setLenient()
            .create()
        
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}


