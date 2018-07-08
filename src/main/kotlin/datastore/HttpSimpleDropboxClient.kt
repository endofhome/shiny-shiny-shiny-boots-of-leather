package datastore

import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_DROPBOX_ACCESS_TOKEN
import com.dropbox.core.DbxApiException
import com.dropbox.core.DbxException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import config.Configuration
import datastore.WriteState.WriteFailure
import datastore.WriteState.WriteSuccess
import java.io.ByteArrayInputStream
import java.io.IOException

interface SimpleDropboxClient {
    fun readFile(filename: String): String
    fun writeFile(fileContents: String, filename: String): WriteState
}

class HttpSimpleDropboxClient(identifier: String, config: Configuration) : SimpleDropboxClient {
    private val requestConfig: DbxRequestConfig = DbxRequestConfig.newBuilder(identifier).build()
    private val client: DbxClientV2 = DbxClientV2(requestConfig, config.get(KOTLIN_GMAILER_DROPBOX_ACCESS_TOKEN))

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
            WriteSuccess()
        } catch (e: Exception) {
            when (e) {
                is DbxApiException,
                is DbxException,
                is IOException      -> WriteFailure()
                else                -> throw e
            }
        }
    }
}

sealed class WriteState {
    class WriteSuccess : WriteState()
    class WriteFailure : WriteState()
}

