package dev.opengdrive.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DriveApi(
    private val client: OkHttpClient = OkHttpClient(),
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
) {
    private val filePageAdapter = moshi.adapter(DriveFilePage::class.java)

    suspend fun listMarkdownFiles(accessToken: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", MARKDOWN_QUERY)
                .addQueryParameter("orderBy", "modifiedTime desc")
                .addQueryParameter("pageSize", "100")
                .addQueryParameter(
                    "fields",
                    "nextPageToken,files(id,name,modifiedTime,size,capabilities(canEdit,canDownload))",
                )
                .addQueryParameter("spaces", "drive")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val response = client.newCall(authorizedRequest(url.toString(), accessToken).build()).execute()
            response.use {
                ensureSuccessful(it.code, it.body?.string().orEmpty()).also { body ->
                    val page = filePageAdapter.fromJson(body) ?: DriveFilePage()
                    files += page.files.filter(DriveFile::isMarkdown)
                    pageToken = page.nextPageToken
                }
            }
        } while (pageToken != null)
        files
    }

    suspend fun download(file: DriveFile, accessToken: String): OpenDriveFile = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/${file.id}".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()
        client.newCall(authorizedRequest(url.toString(), accessToken).build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            ensureSuccessful(response.code, body)
            OpenDriveFile(file, body, response.header("ETag"))
        }
    }

    suspend fun update(
        fileId: String,
        markdown: String,
        accessToken: String,
        etag: String?,
    ): String? = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "media")
            .build()
        val request = authorizedRequest(url.toString(), accessToken)
            .patch(markdown.toRequestBody(MARKDOWN_MEDIA_TYPE))
            .apply { etag?.let { header("If-Match", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            ensureSuccessful(response.code, body)
            response.header("ETag")
        }
    }

    private fun authorizedRequest(url: String, accessToken: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $accessToken")

    private fun ensureSuccessful(code: Int, body: String): String {
        when (code) {
            in 200..299 -> return body
            401 -> throw DriveException.Unauthorized()
            409, 412 -> throw DriveException.Conflict()
            else -> throw DriveException.Http(code, body.take(300))
        }
    }

    companion object {
        internal const val MARKDOWN_QUERY =
            "trashed = false and name contains '.md' and mimeType != 'application/vnd.google-apps.folder'"
        private val MARKDOWN_MEDIA_TYPE = "text/markdown; charset=utf-8".toMediaType()
    }
}
