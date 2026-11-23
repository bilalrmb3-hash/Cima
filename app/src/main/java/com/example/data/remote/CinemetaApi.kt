package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface CinemetaApi {
    @GET("catalog/{type}/{catalogId}.json")
    suspend fun getCatalog(
        @Path("type") type: String,
        @Path("catalogId") catalogId: String = "top"
    ): CatalogResponseDto

    @GET("catalog/{type}/{catalogId}/genre={genre}.json")
    suspend fun getCatalogByGenre(
        @Path("type") type: String,
        @Path("catalogId") catalogId: String = "top",
        @Path("genre") genre: String
    ): CatalogResponseDto

    @GET("catalog/{type}/{catalogId}/search={query}.json")
    suspend fun searchCatalog(
        @Path("type") type: String,
        @Path("catalogId") catalogId: String = "top",
        @Path("query") query: String
    ): CatalogResponseDto

    @GET("meta/{type}/{id}.json")
    suspend fun getMeta(
        @Path("type") type: String,
        @Path("id") id: String
    ): MetaResponseDto

    companion object {
        private const val BASE_URL = "https://v3-cinemeta.strem.io/"

        fun create(): CinemetaApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(CinemetaApi::class.java)
        }
    }
}
