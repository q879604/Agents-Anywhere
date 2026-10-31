package com.agentsanywhere.app.api

import com.agentsanywhere.app.config.AppConfig
import okhttp3.Request
import org.json.JSONObject

data class AndroidAppRelease(
    val versionName: String,
    val downloadUrl: String,
)

class AppUpdatesApi(private val client: ApiClient = ApiClient()) {
    fun check(serverUrl: String, currentVersionName: String): AndroidAppRelease? {
        personalRelease(currentVersionName)?.let { return it }
        serverRelease(serverUrl, currentVersionName)?.let { return it }
        val payload = client.getJson(
            serverUrl = serverUrl,
            path = "/health",
        )
        if (payload.optString("status") != "ok") throw ApiException("Server health check failed.")
        val latestVersion = payload.optString("version").trim()
        val comparison = compareUpdateVersions(latestVersion, currentVersionName)
            ?: throw ApiException("Server health response has no valid version.")
        if (comparison <= 0) return null
        return AndroidAppRelease(
            versionName = latestVersion,
            downloadUrl = AppConfig.UPDATE_DOWNLOAD_URL,
        )
    }

    private fun serverRelease(serverUrl: String, currentVersionName: String): AndroidAppRelease? {
        // GitHub Releases may be unreachable on some mobile networks. The
        // authenticated self-hosted server exposes the same tiny manifest so
        // update checks still work without a third-party network dependency.
        val payload = runCatching { client.getJson(serverUrl, "/app/latest") }.getOrNull()
            ?: return null
        val versionName = payload.optString("versionName").trim()
        val downloadUrl = payload.optString("downloadUrl").trim()
        if (versionName.isBlank() || downloadUrl.isBlank()) return null
        val comparison = compareUpdateVersions(versionName, currentVersionName) ?: return null
        if (comparison <= 0) return null
        return AndroidAppRelease(versionName = versionName, downloadUrl = downloadUrl)
    }

    private fun personalRelease(currentVersionName: String): AndroidAppRelease? {
        // The manifest lives on a static download host, not on the API server:
        // ApiClient would prepend /api/v2, and this endpoint is a release asset on GitHub.
        // Fetch it directly so the no-cache endpoint is reachable from any server.
        val manifest = runCatching {
            val url = AppConfig.PERSONAL_UPDATE_MANIFEST_BASE.trimEnd('/') +
                AppConfig.PERSONAL_UPDATE_MANIFEST_PATH
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            HttpClientProvider.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                JSONObject(response.body?.string().orEmpty())
            }
        }.getOrNull() ?: return null
        val versionName = manifest.optString("versionName").trim()
        val downloadUrl = manifest.optString("downloadUrl").trim()
        if (versionName.isBlank() || downloadUrl.isBlank()) return null
        val comparison = compareUpdateVersions(versionName, currentVersionName) ?: return null
        if (comparison <= 0) return null
        return AndroidAppRelease(versionName = versionName, downloadUrl = downloadUrl)
    }
}
