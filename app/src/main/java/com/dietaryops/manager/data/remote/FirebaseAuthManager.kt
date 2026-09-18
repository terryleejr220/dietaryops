package com.dietaryops.manager.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthManager(
    private val lazyAuth: Lazy<FirebaseAuth?> = lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }
    }
) {
    val auth: FirebaseAuth?
        get() = lazyAuth.value

    val currentUser: FirebaseUser?
        get() = auth?.currentUser

    val currentUserFlow: Flow<FirebaseUser?> = callbackFlow {
        val authInstance = auth
        if (authInstance == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        authInstance.addAuthStateListener(listener)
        awaitClose {
            authInstance.removeAuthStateListener(listener)
        }
    }

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        val authInstance = auth ?: return Result.failure(Exception("FirebaseAuth instance is null"))
        return try {
            val authResult = authInstance.signInWithEmailAndPassword(email.trim(), password.trim()).await()
            val user = authResult.user
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Sign in returned null user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(email: String, password: String): Result<FirebaseUser> {
        val authInstance = auth ?: return Result.failure(Exception("FirebaseAuth instance is null"))
        return try {
            val authResult = authInstance.createUserWithEmailAndPassword(email.trim(), password.trim()).await()
            val user = authResult.user
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Sign up returned null user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth?.signOut()
    }
}
