package com.example.raphael.api

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import java.net.InetAddress
import java.net.Socket
import okhttp3.ConnectionSpec
import okhttp3.Protocol

object NetworkClient {
    private const val TAG = "NetworkClient"
    private const val BASE_URL = "http://192.168.164.129:5000/"  // Android localhost

    init {
        // Test connection to server on initialization
        try {
            Log.d(TAG, "Testing connection to server...")
            val socket = Socket()
            val address = InetAddress.getByName("10.0.2.2")
            socket.connect(java.net.InetSocketAddress(address, 5000), 5000)
            socket.close()
            Log.d(TAG, "Server is reachable")
        } catch (e: Exception) {
            Log.e(TAG, "Server is not reachable: ${e.message}", e)
        }
    }

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("Authorization")
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val original = chain.request()
            val url = original.url
            
            // Log DNS resolution attempt
            try {
                Log.d(TAG, "Attempting to resolve host: ${url.host}")
                val addresses = InetAddress.getAllByName(url.host)
                addresses.forEach { address ->
                    Log.d(TAG, "Resolved ${url.host} to: ${address.hostAddress}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "DNS resolution failed for ${url.host}: ${e.message}", e)
            }
            
            Log.d(TAG, "Making request to: $url")
            Log.d(TAG, "Request method: ${original.method}")
            Log.d(TAG, "Request headers: ${original.headers}")
            
            val request = original.newBuilder()
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method(original.method, original.body)
                .build()

            try {
                val response = chain.proceed(request)
                Log.d(TAG, "Response code: ${response.code}")
                Log.d(TAG, "Response headers: ${response.headers}")
                if (!response.isSuccessful) {
                    Log.e(TAG, "Error response body: ${response.peekBody(Long.MAX_VALUE).string()}")
                }
                response
            } catch (e: Exception) {
                Log.e(TAG, "Network error during request to $url: ${e.message}", e)
                Log.e(TAG, "Stack trace: ", e)
                throw e
            }
        }
        .connectionSpecs(listOf(
            ConnectionSpec.CLEARTEXT,
            ConnectionSpec.MODERN_TLS,
            ConnectionSpec.COMPATIBLE_TLS
        ))
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            try {
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                Log.e(TAG, "SSL configuration error: ${e.message}", e)
            }
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authService: AuthApiService = retrofit.create(AuthApiService::class.java)
    val analysisService: AnalysisApiService = retrofit.create(AnalysisApiService::class.java)
} 