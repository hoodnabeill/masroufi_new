package com.masroufi.app.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteFile(val bytes: ByteArray, val etag: String?)

sealed class WebDavException(message: String) : IOException(message) {
    class Unauthorized : WebDavException("unauthorized")
    class PreconditionFailed : WebDavException("precondition failed")
    class Http(val code: Int) : WebDavException("http $code")
    class InvalidUrl : WebDavException("invalid url")
}

@Singleton
class WebDavClient @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Only HTTPS is accepted, except loopback hosts used for local testing. */
    fun fileUrl(base: String, fileName: String): HttpUrl {
        val url = base.trim().toHttpUrlOrNull() ?: throw WebDavException.InvalidUrl()
        val loopback = url.host == "localhost" || url.host == "127.0.0.1"
        if (!url.isHttps && !loopback) throw WebDavException.InvalidUrl()
        return url.newBuilder().addPathSegment(fileName).build()
    }

    suspend fun get(url: HttpUrl, user: String, password: String): RemoteFile? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).auth(user, password).get().build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                response.code == 401 || response.code == 403 -> throw WebDavException.Unauthorized()
                !response.isSuccessful -> throw WebDavException.Http(response.code)
                else -> RemoteFile(response.body?.bytes() ?: ByteArray(0), response.header("ETag"))
            }
        }
    }

    /**
     * Uploads with If-Match (or If-None-Match: * for a new file) so a concurrent upload from another device is
     * detected instead of silently overwritten. Servers that send no ETag get an unconditional upload.
     */
    suspend fun put(
        url: HttpUrl,
        user: String,
        password: String,
        bytes: ByteArray,
        expectedEtag: String?,
        remoteExists: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).auth(user, password)
            .put(bytes.toRequestBody("application/octet-stream".toMediaType()))
        when {
            expectedEtag != null -> builder.header("If-Match", expectedEtag)
            !remoteExists -> builder.header("If-None-Match", "*")
        }
        client.newCall(builder.build()).execute().use { response ->
            when {
                response.code == 412 -> throw WebDavException.PreconditionFailed()
                response.code == 401 || response.code == 403 -> throw WebDavException.Unauthorized()
                !response.isSuccessful -> throw WebDavException.Http(response.code)
                else -> response.header("ETag")
            }
        }
    }

    private fun Request.Builder.auth(user: String, password: String): Request.Builder =
        if (user.isEmpty()) this else header("Authorization", Credentials.basic(user, password, Charsets.UTF_8))
}
