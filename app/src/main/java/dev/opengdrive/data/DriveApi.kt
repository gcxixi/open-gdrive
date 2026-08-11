package dev.opengdrive.data

import com.squareup.moshi.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.util.UUID

class DriveApi(
    private val client: OkHttpClient = OkHttpClient(),
) {
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
                    val page = parseFilePage(body)
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

    suspend fun getMetadata(fileId: String, accessToken: String): DriveMetadata = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter(
                "fields",
                "id,name,mimeType,modifiedTime,size,webViewLink,capabilities(canEdit,canDownload)",
            )
            .build()
        client.newCall(authorizedRequest(url.toString(), accessToken).build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            ensureSuccessful(response.code, body)
            val file = parseDriveFileJson(body)
                ?: throw DriveException.Http(response.code, "Drive returned no file metadata")
            DriveMetadata(file, response.header("ETag"))
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

    suspend fun createMarkdown(
        name: String,
        parentId: String,
        markdown: String,
        localId: String,
        accessToken: String,
    ): OpenDriveFile = withContext(Dispatchers.IO) {
        val boundary = "open-gdrive-${UUID.randomUUID()}"
        val metadata = createMetadataJson(name, parentId, localId)
        val multipart = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\n")
            append("Content-Type: text/markdown; charset=UTF-8\r\n\r\n")
            append(markdown)
            append("\r\n--$boundary--\r\n")
        }
        val url = "https://www.googleapis.com/upload/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter(
                "fields",
                "id,name,mimeType,modifiedTime,size,webViewLink,capabilities(canEdit,canDownload)",
            )
            .build()
        client.newCall(
            authorizedRequest(url.toString(), accessToken)
                .post(multipart.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                .build(),
        ).execute().use { response ->
            val body = response.body?.string().orEmpty()
            ensureSuccessful(response.code, body)
            val file = parseDriveFileJson(body)
                ?: throw DriveException.Http(response.code, "Drive returned no file metadata")
            OpenDriveFile(file, PreviewData.Markdown(markdown), response.header("ETag"))
        }
    }

    suspend fun rename(fileId: String, name: String, accessToken: String): String? =
        withContext(Dispatchers.IO) {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId".toHttpUrl().newBuilder()
                .addQueryParameter("fields", "id,name")
                .build()
            val body = "{\"name\":${jsonString(name)}}".toRequestBody(JSON_MEDIA_TYPE)
            client.newCall(authorizedRequest(url.toString(), accessToken).patch(body).build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                ensureSuccessful(response.code, responseBody)
                response.header("ETag")
            }
        }

    suspend fun trash(fileId: String, accessToken: String) = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "id,trashed")
            .build()
        val body = "{\"trashed\":true}".toRequestBody(JSON_MEDIA_TYPE)
        client.newCall(authorizedRequest(url.toString(), accessToken).patch(body).build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            ensureSuccessful(response.code, responseBody)
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
        internal fun parseFilePage(json: String): DriveFilePage {
            val reader = JsonReader.of(Buffer().writeUtf8(json))
            val files = mutableListOf<DriveFile>()
            var nextPageToken: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "nextPageToken" -> nextPageToken = reader.nextNullableString()
                    "files" -> {
                        reader.beginArray()
                        while (reader.hasNext()) parseDriveFile(reader)?.let(files::add)
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return DriveFilePage(files, nextPageToken)
        }

        internal fun parseDriveFileJson(json: String): DriveFile? {
            val reader = JsonReader.of(Buffer().writeUtf8(json))
            return parseDriveFile(reader)
        }

        private fun parseDriveFile(reader: JsonReader): DriveFile? {
            var id: String? = null
            var name: String? = null
            var mimeType = "application/octet-stream"
            var modifiedTime: String? = null
            var size: String? = null
            var webViewLink: String? = null
            var capabilities: DriveCapabilities? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextNullableString()
                    "name" -> name = reader.nextNullableString()
                    "mimeType" -> mimeType = reader.nextNullableString() ?: mimeType
                    "modifiedTime" -> modifiedTime = reader.nextNullableString()
                    "size" -> size = reader.nextNullableString()
                    "webViewLink" -> webViewLink = reader.nextNullableString()
                    "capabilities" -> capabilities = parseCapabilities(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return if (id != null && name != null) {
                DriveFile(id, name, mimeType, modifiedTime, size, webViewLink, capabilities)
            } else {
                null
            }
        }

        private fun parseCapabilities(reader: JsonReader): DriveCapabilities {
            var canEdit = false
            var canDownload = true
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "canEdit" -> canEdit = reader.nextBoolean()
                    "canDownload" -> canDownload = reader.nextBoolean()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return DriveCapabilities(canEdit, canDownload)
        }

        private fun JsonReader.nextNullableString(): String? =
            if (peek() == JsonReader.Token.NULL) nextNull() else nextString()

        internal fun childQuery(folderId: String): String {
            if (folderId == "all") return "trashed = false"
            val escaped = folderId.replace("\\", "\\\\").replace("'", "\\'")
            return "trashed = false and '$escaped' in parents"
        }

        internal fun createMetadataJson(name: String, parentId: String, localId: String): String = buildString {
            append("{\"name\":")
            append(jsonString(name))
            append(",\"mimeType\":\"text/markdown\"")
            append(",\"appProperties\":{\"openGDriveLocalId\":")
            append(jsonString(localId))
            append('}')
            if (parentId != "all") {
                append(",\"parents\":[")
                append(jsonString(parentId))
                append(']')
            }
            append('}')
        }

        private fun jsonString(value: String): String = buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
            append('"')
        }

        private const val MAX_PREVIEW_MB = 25
        private const val MAX_PREVIEW_BYTES = MAX_PREVIEW_MB * 1024 * 1024L
        private val MARKDOWN_MEDIA_TYPE = "text/markdown; charset=utf-8".toMediaType()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
