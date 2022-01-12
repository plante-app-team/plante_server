package vegancheckteam.plante_server.aws

import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
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
     */
    suspend fun putData(key: String, data: InputStream) {
        val objectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
        runOnIO {
            val bytes = data.readAllBytes()
            data.close()
            client.putObject(objectRequest, RequestBody.fromBytes(bytes))
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
        return runOnIO {
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
        runOnIO {
            client.deleteObject(deleteObjectRequest)
        }
    }

    private suspend fun <R> runOnIO(block: () -> R): R {
        return withContext(Dispatchers.IO) {
            runCatching(block)
        }.getOrThrow()
    }
}