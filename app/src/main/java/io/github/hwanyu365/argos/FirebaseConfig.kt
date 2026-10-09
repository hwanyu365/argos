package io.github.hwanyu365.argos

data class FirebaseConfig(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val databaseUrl: String
) {
    val isComplete: Boolean
        get() = listOf(apiKey, appId, projectId, databaseUrl).none { it.isBlank() }

    companion object {
        fun fromBuildConfig() = FirebaseConfig(
            apiKey = BuildConfig.FIREBASE_API_KEY,
            appId = BuildConfig.FIREBASE_APP_ID,
            projectId = BuildConfig.FIREBASE_PROJECT_ID,
            databaseUrl = BuildConfig.FIREBASE_DATABASE_URL
        )
    }
}
