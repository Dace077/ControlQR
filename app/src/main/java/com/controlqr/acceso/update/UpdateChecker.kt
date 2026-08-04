package com.controlqr.acceso.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String?,
    val pageUrl: String
)

/**
 * Consulta la última publicación en GitHub Releases.
 *
 * Es la única función de la app que usa internet, y es opcional: si no hay red,
 * el control de accesos sigue funcionando igual.
 */
class UpdateChecker {

    suspend fun latestRelease(repo: String): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/$repo/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ControlQR-Android")
            }

            try {
                if (connection.responseCode != 200) {
                    error("GitHub respondió ${connection.responseCode}")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)

                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }

                ReleaseInfo(
                    versionName = json.optString("tag_name").removePrefix("v"),
                    notes = json.optString("body").take(600),
                    apkUrl = apkUrl,
                    pageUrl = json.optString("html_url")
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Compara versiones tipo "1.4.2" segmento por segmento. Los sufijos no numéricos
     * ("-debug", "-rc1") se ignoran para no reportar actualizaciones falsas.
     */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(value: String) = value.trim()
            .substringBefore('-')
            .split('.')
            .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

        val remoteParts = parts(remote)
        val localParts = parts(local)
        val size = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until size) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
