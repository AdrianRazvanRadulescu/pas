package dev.pas.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Base64

/**
 * The private data repository: whole JSON files through the contents API,
 * and the app's own APK from its releases (published by publish.mjs, tagged "v<versionCode>").
 */
class GitHubFiles(private val token: String, private val repository: String) {

    class RemoteFile(val text: String, val sha: String)

    /** [notes] is the text given to publish.mjs, shown as "What's new". */
    class Release(val versionCode: Long, val apkAssetId: Long, val notes: String)

    fun read(path: String): RemoteFile {
        val request = Request.Builder()
            .url(fileUrl(path))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(describeFailure(response.code, path))
            val file = Json.parseToJsonElement(response.body!!.string()).jsonObject
            val base64Content = file.getValue("content").jsonPrimitive.content
            val text = String(Base64.getMimeDecoder().decode(base64Content), Charsets.UTF_8)
            return RemoteFile(text, file.getValue("sha").jsonPrimitive.content)
        }
    }

    fun write(path: String, text: String, previousSha: String, commitMessage: String) {
        val payload = JsonObject(
            mapOf(
                "message" to JsonPrimitive(commitMessage),
                "content" to JsonPrimitive(Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))),
                "sha" to JsonPrimitive(previousSha),
            )
        )
        val request = Request.Builder()
            .url(fileUrl(path))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .put(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(describeFailure(response.code, path))
        }
    }

    /** Null when the repository has no release with an APK yet. */
    fun latestRelease(): Release? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repository/releases/latest")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException(describeFailure(response.code, "releases"))
            val release = Json.parseToJsonElement(response.body!!.string()).jsonObject
            val apkAsset = release.getValue("assets").jsonArray
                .map { it.jsonObject }
                .firstOrNull { it.getValue("name").jsonPrimitive.content.endsWith(".apk") }
                ?: return null
            return Release(
                versionCode = release.getValue("tag_name").jsonPrimitive.content.removePrefix("v").toLong(),
                apkAssetId = apkAsset.getValue("id").jsonPrimitive.content.toLong(),
                // GitHub sends "body": null for a release published without notes.
                notes = release["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }

    /** GitHub redirects to a signed download URL on another host; OkHttp drops the token on that hop by itself. */
    fun downloadReleaseAsset(assetId: Long, destination: File) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repository/releases/assets/$assetId")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/octet-stream")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(describeFailure(response.code, "pas.apk"))
            destination.parentFile!!.mkdirs()
            destination.outputStream().use { output -> response.body!!.byteStream().copyTo(output) }
        }
    }

    private fun fileUrl(path: String) = "https://api.github.com/repos/$repository/contents/$path"

    private fun describeFailure(statusCode: Int, path: String): String = when (statusCode) {
        401 -> "GitHub rejected the token (401). Check it in settings."
        403 -> "The token is not allowed to use $repository (403). It needs Contents: Read and write."
        404 -> "Cannot find $path in $repository (404). Check the repository name and the token's access."
        409 -> "$path changed in the meantime (409). Will retry on the next sync."
        else -> "GitHub answered $statusCode for $path."
    }

    private companion object {
        val client = OkHttpClient()
    }
}
