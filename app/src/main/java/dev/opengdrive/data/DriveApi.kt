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
import okhttp3.Response

class DriveApi(
    private val client: OkHttpClient = OkHttpClient(),
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
) {
    private val filePageAdapter = moshi.adapter(DriveFilePage::class.java)

    suspend fun listFiles(accessToken: String, folderId: String = "all"): List<DriveFile> =
        withContext(Dispatchers.IO) {
            val files = mutableListOf<DriveFile>()
            var pageToken: String? = null
            do {
                val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                    .addQueryParameter("q", childQuery(folderId))
                    .addQueryParameter("orderBy", "folder,name_natural")
                    .addQueryParameter("pageSize", "100")
                    .addQueryParameter(
                        "fields",
                        "nextPageToken,files(id,name,mimeType,modifiedTime,size,webViewLink," +
                            "capabilities(canEdit,canDownload))",
                    )
                    .addQueryParameter("spaces", "drive")
                    .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                    .build()
                client.newCall(authorizedRequest(url.toString(), accessToken).build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    ensureSuccessful(response.code, body)
                    val page = filePageAdapter.fromJson(body) ?: DriveFilePage()
                    files += page.files
                    pageToken = page.nextPageToken
                }
            } while (pageToken != null)
            files
        }

    suspend fun open(file: DriveFile, accessToken: String): OpenDriveFile = withContext(Dispatchers.IO) {
        when (val spec = file.previewSpec()) {
            is PreviewSpec.Unsupported -> OpenDriveFile(file, PreviewData.Unsupported(spec.reason), null)
            is PreviewSpec.Download -> fetchPreview(file, accessToken, spec.kind, exportMimeType = null)
            is PreviewSpec.Export -> fetchPreview(file, accessToken, spec.kind, spec.mimeType)
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

    private fun fetchPreview(
        file: DriveFile,
        accessToken: String,
        kind: PreviewKind,
        exportMimeType: String?,
    ): OpenDriveFile {
        val base = if (exportMimeType == null) {
            "https://www.googleapis.com/drive/v3/files/${file.id}".toHttpUrl().newBuilder()
                .addQueryParameter("alt", "media")
        } else {
            "https://www.googleapis.com/drive/v3/files/${file.id}/export".toHttpUrl().newBuilder()
                .addQueryParameter("mimeType", exportMimeType)
        }
        client.newCall(authorizedRequest(base.build().toString(), accessToken).build()).execute().use { response ->
            ensureDownloadSuccessful(response)
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (bytes.size > MAX_PREVIEW_BYTES) throw DriveException.TooLarge(MAX_PREVIEW_MB)
            val preview = when (kind) {
                PreviewKind.Markdown -> PreviewData.Markdown(bytes.toString(Charsets.UTF_8))
                is PreviewKind.Text -> PreviewData.Text(bytes.toString(Charsets.UTF_8), kind.label)
                PreviewKind.Image -> PreviewData.Image(bytes, exportMimeType ?: file.mimeType)
                PreviewKind.Pdf -> PreviewData.Pdf(bytes)
            }
            return OpenDriveFile(file, preview, response.header("ETag"))
        }
    }

    private fun ensureDownloadSuccessful(response: Response) {
        val contentLength = response.body?.contentLength() ?: -1
        if (contentLength > MAX_PREVIEW_BYTES) throw DriveException.TooLarge(MAX_PREVIEW_MB)
        if (!response.isSuccessful) {
            ensureSuccessful(response.code, response.body?.string().orEmpty())
        }
    }

    private fun authorizedRequest(url: String, accessToken: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $accessToken")

    private fun ensureSuccessful(code: Int, body: String) {
        when (code) {
            in 200..299 -> Unit
            401 -> throw DriveException.Unauthorized()
            409, 412 -> throw DriveException.Conflict()
            else -> throw DriveException.Http(code, body.take(300))
        }
    }

    companion object {
        internal fun childQuery(folderId: String): String {
            if (folderId == "all") return "trashed = false"
            val escaped = folderId.replace("\\", "\\\\").replace("'", "\\'")
            return "trashed = false and '$escaped' in parents"
        }

        private const val MAX_PREVIEW_MB = 25
        private const val MAX_PREVIEW_BYTES = MAX_PREVIEW_MB * 1024 * 1024L
        private val MARKDOWN_MEDIA_TYPE = "text/markdown; charset=utf-8".toMediaType()
    }
}
