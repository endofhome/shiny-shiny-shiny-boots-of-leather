import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import java.io.ByteArrayInputStream


class SimpleDropboxClient(identifier: String, accessToken: String) {
    private val config = DbxRequestConfig.newBuilder(identifier).build()
    private val client = DbxClientV2(config, accessToken)

    fun readFile(filename: String): String {
        return client.files().download(filename).inputStream.reader().readText()
    }

    fun writeFile(fileContents: String, filename: String) {
        ByteArrayInputStream(fileContents.toByteArray()).use { inputStream ->
            client.files().uploadBuilder(filename)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream)
        }
    }
}