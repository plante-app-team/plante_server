package vegancheckteam.plante_server.proxy

import io.ktor.application.ApplicationCall
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.request.receiveMultipart
import io.ktor.util.url
import io.ktor.utils.io.streams.asInput
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.multipart_proxy.MultipartProxyStorage

abstract class MultipartProxy(
    private val storage: MultipartProxyStorage,
) {
    suspend fun proxy(call: ApplicationCall, client: HttpClient, testing: Boolean): HttpResponse {
        val targetUrl = convertUrl(call.url(), testing)
        val (files, formItems) = receiveMultiformParts(call, targetUrl)
        val result = try {
            proxyImpl(call, targetUrl, formItems, files, client, testing)
        } finally {
            files.forEach { it.localFile.delete() }
        }
        ensureCredentialsWereRemovedFromHeaders(call, result)
        return result
    }

    private suspend fun receiveMultiformParts(
        call: ApplicationCall,
        targetUrl: String,
    ): Pair<List<ProxiedFileData>, List<PartData.FormItem>> {
        val filesMap = mutableMapOf<String, ProxiedFileData>()
        val multipartData = call.receiveMultipart()
        val formItems = mutableListOf<PartData.FormItem>()
        multipartData.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    val name = part.name ?: throw Exception("Nameless parts are not supported")
                    if (!filesMap.contains(name)) {
                        val file = storage.provideTempFile()!!
                        val stream = file.outputStream()
                        filesMap[name] = ProxiedFileData(
                            name,
                            part.originalFileName,
                            file,
                            stream,
                            part.headers,
                        )
                        Log.i("MultipartProxy", "target: $targetUrl, receiving file: $name (${part.originalFileName})")
                    }
                    val stream = filesMap[part.name]!!.localFileStream
                    runOnIO { stream.write(part.streamProvider().readBytes()) }
                }
                is PartData.FormItem -> {
                    formItems.add(part)
                }
                is PartData.BinaryItem -> {
                    throw Exception("Binary multipart data is not supported")
                }
            }
        }
        for (file in filesMap.values) {
            runOnIO { file.localFileStream.close() }
            Log.i("MultipartProxy", "target: $targetUrl, file ${file.fieldName} has size ${file.localFile.length()}")
        }
        return Pair(filesMap.values.toList(), formItems)
    }

    private suspend fun proxyImpl(
        call: ApplicationCall,
        targetUrl: String,
        formItems: List<PartData.FormItem>,
        files: List<ProxiedFileData>,
        client: HttpClient,
        testing: Boolean,
    ): HttpResponse {
        val filesFormData = formData {
            for (file in files) {
                val inputProvider = InputProvider { file.localFile.inputStream().asInput() }
                append(file.fieldName, inputProvider, file.headers)
            }
        }
        val additionalFormItems = additionalFormData(testing)
        val additionalFormItemsNames = additionalFormItems.map { it.name }
        val cleanedFormItems = formItems.filter { !additionalFormItemsNames.contains(it.name) }
        val partData = cleanedFormItems + filesFormData + additionalFormItems
        val result = client.submitFormWithBinaryData<HttpResponse>(targetUrl, partData) {
            headers.appendAll(proxyHeaders(call))
            for (header in additionalHeaders(testing)) {
                headers.append(header.key, header.value)
            }
            Log.i("MultipartProxy", "target: $targetUrl, headers: ${headers.entries()}")
        }
        return result
    }

    private suspend fun <R> runOnIO(block: () -> R) {
        withContext(Dispatchers.IO) {
            runCatching(block)
        }.getOrThrow()
    }

    private data class ProxiedFileData(
        val fieldName: String,
        val fileName: String?,
        val localFile: File,
        val localFileStream: OutputStream,
        val headers: Headers,
    )

    protected abstract fun convertUrl(url: String, testing: Boolean): String
    protected open fun additionalHeaders(testing: Boolean): Map<String, String> = emptyMap()
    protected open fun additionalFormData(testing: Boolean): List<PartData> = emptyList()
}
