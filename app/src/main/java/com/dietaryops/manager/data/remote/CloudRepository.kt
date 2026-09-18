package com.dietaryops.manager.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class CloudCompanyConfig(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val spreadsheetId: String = "",
    val webAppUrl: String = "",
    val departments: List<String> = emptyList(),
    val departmentTabs: Map<String, String> = emptyMap(),
    val active: Boolean = true
)

data class CloudStaffUser(
    val employeeId: String = "",
    val displayName: String = "",
    val companyCode: String = "",
    val department: String = "",
    val role: String = "",
    val pin: String = "",
    val badgeToken: String = "",
    val active: Boolean = true
)

class CloudRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun authenticateWithBadgeToken(token: String): Pair<CloudStaffUser, CloudCompanyConfig>? {
        try {
            // Because we don't know the company code just from the token (unless we encoded it),
            // we will query across all users via a collectionGroup query.
            val usersQuerySnapshot = db.collectionGroup("users")
                .whereEqualTo("badgeToken", token)
                .whereEqualTo("active", true)
                .limit(1)
                .get()
                .await()

            if (usersQuerySnapshot.isEmpty) {
                Log.e("CloudRepository", "No active user found with provided badge token.")
                return null
            }

            val userDoc = usersQuerySnapshot.documents.first()
            val user = userDoc.toObject(CloudStaffUser::class.java) ?: return null

            // Now fetch the company config
            val companyDoc = db.collection("companies").document(user.companyCode).get().await()
            if (!companyDoc.exists()) {
                Log.e("CloudRepository", "Company not found for code: ${user.companyCode}")
                return null
            }

            val company = companyDoc.toObject(CloudCompanyConfig::class.java) ?: return null
            if (!company.active) {
                Log.e("CloudRepository", "Company is not active.")
                return null
            }

            return Pair(user, company)
        } catch (e: Exception) {
            Log.e("CloudRepository", "Error authenticating badge: ${e.message}", e)
            return null
        }
    }
}
