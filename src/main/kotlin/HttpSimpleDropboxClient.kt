import WriteState.Failure
import WriteState.Success
import com.dropbox.core.DbxApiException
import com.dropbox.core.DbxException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import java.io.ByteArrayInputStream
import java.io.IOException

interface SimpleDropboxClient {
    fun readFile(filename: String): String
    fun writeFile(fileContents: String, filename: String): WriteState
}

class HttpSimpleDropboxClient(identifier: String, accessToken: String) : SimpleDropboxClient {
    private val config: DbxRequestConfig = DbxRequestConfig.newBuilder(identifier).build()
    private val client: DbxClientV2 = DbxClientV2(config, accessToken)

    override fun readFile(filename: String): String {
        return client.files().download(filename).inputStream.reader().readText()
    }

    override fun writeFile(fileContents: String, filename: String): WriteState {
        return try {
            ByteArrayInputStream(fileContents.toByteArray()).use { inputStream ->
                client.files().uploadBuilder(filename)
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(inputStream)
            }
            Success()
        } catch (e: Exception) {
            when (e) {
                is DbxApiException,
                is DbxException,
                is IOException      -> Failure()
                else                -> throw e
            }
        }
    }
}

open class StubDropboxClient(initialFiles: List<FileLike>) : SimpleDropboxClient {
    private var files = initialFiles

    override fun readFile(filename: String): String {
        val fileMaybe = files.find { it.name == filename }
        return fileMaybe?.contents ?: ""
    }

    override fun writeFile(fileContents: String, filename: String): WriteState = Success()
}

class StubDropboxClientThatCannotStore(initialFiles: List<FileLike>) : StubDropboxClient(initialFiles) {
    override fun writeFile(fileContents: String, filename: String): WriteState = Failure()
}

sealed class WriteState {
    class Success : WriteState()
    class Failure : WriteState()
}

data class FileLike(val name: String, val contents: String)
