package com.example.franwan.auth

import android.util.Log
import com.example.franwan.network.LoginRequest
import com.example.franwan.network.NetworkModule

class AuthRepository(private val sessionManager: SessionManager) {
    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val response = NetworkModule.api.login(LoginRequest(email, password))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    sessionManager.saveAuthToken(body.token)
                    body.user.username.let { sessionManager.saveUserDisplayName(it) }
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Réponse vide"))
                }
            } else {
                Log.e("AuthRepository", "Login HTTP error: ${response.code()}, body: ${response.errorBody()?.string()}")
                Result.failure(IllegalStateException("Erreur ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login error: ${e.message}", e)
            // Afficher plus de détails sur l'erreur
            if (e.message?.contains("malformed JSON") == true) {
                Log.e("AuthRepository", "L'API renvoie probablement du HTML au lieu de JSON (anti-bot InfinityFree)")
            }
            Result.failure(e)
        }
    }

    suspend fun refreshProfile(): Result<Unit> {
        val token = sessionManager.getAuthToken() ?: return Result.failure(IllegalStateException("Non authentifié"))
        return try {
            val response = NetworkModule.api.me("Bearer $token")
            if (response.isSuccessful) {
                response.body()?.user?.username?.let { sessionManager.saveUserDisplayName(it) }
                Result.success(Unit)
            } else {
                Log.e("AuthRepository", "Me HTTP error: ${response.code()}, body: ${response.errorBody()?.string()}")
                Result.failure(IllegalStateException("Erreur ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Me error: ${e.message}", e)
            Result.failure(e)
        }
    }
}


