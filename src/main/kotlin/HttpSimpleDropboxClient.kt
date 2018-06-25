import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import java.io.ByteArrayInputStream

interface SimpleDropboxClient {
    fun readFile(filename: String): String
    fun writeFile(fileContents: String, filename: String)
}

class HttpSimpleDropboxClient(identifier: String, accessToken: String) : SimpleDropboxClient {
    private val config: DbxRequestConfig = DbxRequestConfig.newBuilder(identifier).build()
    private val client: DbxClientV2 = DbxClientV2(config, accessToken)

    override fun readFile(filename: String): String {
        return client.files().download(filename).inputStream.reader().readText()
    }

    override fun writeFile(fileContents: String, filename: String) {
        ByteArrayInputStream(fileContents.toByteArray()).use { inputStream ->
            client.files().uploadBuilder(filename)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream)
        }
    }
}

class StubDropboxClient(initialFiles: List<FileLike>) : SimpleDropboxClient {
    private var files = initialFiles

    override fun readFile(filename: String): String {
        val fileMaybe = files.find { it.name == filename }
        return fileMaybe?.contents ?: ""
    }

    override fun writeFile(fileContents: String, filename: String) { }

}

data class FileLike(val name: String, val contents: String)
