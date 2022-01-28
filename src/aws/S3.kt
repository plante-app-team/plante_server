package vegancheckteam.plante_server.aws

import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import vegancheckteam.plante_server.Config


object S3 {
    private val client: S3Client by lazy {
        S3Client.builder()
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        Config.instance.awsS3AccessKeyId,
                        Config.instance.awsS3SecretAccessKey
                    )))
            .region(Region.of(Config.instance.awsS3Region))
            .build()
    }
    private val bucketName: String by lazy { Config.instance.awsS3BucketName }

    /**
     * NOTE: the function will close the stream after everything is read from it.
     * @param maxBytes - max allowed sized for the passed [data] stream.
     * @throws DataTooLargeException - if given stream is greater than [maxBytes].
     */
    suspend fun putData(key: String, data: InputStream, maxBytes: Int? = null) {
        val objectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
        withContext(Dispatchers.IO) {
            data.use { data ->
                val bytesLimit = if (maxBytes != null) {
                    maxBytes + 1
                } else {
                    Int.MAX_VALUE
                }

                val bytes = data.readNBytes(bytesLimit)
                if (maxBytes != null && maxBytes < bytes.size) {
                    throw DataTooLargeException()
                }
                client.putObject(objectRequest, RequestBody.fromBytes(bytes))
            }
        }
    }

    /**
     * You must not forget to close the stream.
     */
    suspend fun getData(key: String): InputStream? {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
        return withContext(Dispatchers.IO) {
            try {
                client.getObject(getObjectRequest)
            } catch (e: NoSuchKeyException) {
                null
            }
        }
    }

    suspend fun deleteData(key: String) {
        val deleteObjectRequest = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
        withContext(Dispatchers.IO) {
            client.deleteObject(deleteObjectRequest)
        }
    }

    suspend fun listKeys(prefix: String): Flow<String> = flow {
        var listObjectsReqManual = ListObjectsV2Request.builder()
            .bucket(bucketName)
            .prefix(prefix)
            .build()

            var done = false
            while (!done) {
                val listObjResponse = client.listObjectsV2(listObjectsReqManual)
                for (content in listObjResponse.contents()) {
                    emit(content.key())
                }
                if (listObjResponse.nextContinuationToken() == null) {
                    done = true
                }
                listObjectsReqManual = listObjectsReqManual.toBuilder()
                    .continuationToken(listObjResponse.nextContinuationToken())
                    .build()
            }
    }.flowOn(Dispatchers.IO)
}

class DataTooLargeException : Exception()
