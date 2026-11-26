package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val rootError = (generateSequence(exception) { it.cause }
        .firstOrNull { it is FirebaseFirestoreException }
        ?: generateSequence(exception) { it.cause }
        .firstOrNull { it !is CancellationException }
        ?: exception)

    // Only log the fault itself, not identifying caller information. Email, UID
    // and provider data are intentionally excluded from the logcat entry.
    Log.e("FirestoreError", "Firestore Error: ${operationType.value} - ${rootError.message ?: rootError.toString()}")
    return "${operationType.value}: ${rootError.message ?: rootError.toString()}"
}
