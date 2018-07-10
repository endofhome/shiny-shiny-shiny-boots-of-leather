package datastore

import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_DROPBOX_ACCESS_TOKEN
import Result
import Result.Failure
import Result.Success
import com.dropbox.core.DbxApiException
import com.dropbox.core.DbxDownloader
import com.dropbox.core.DbxException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.DownloadErrorException
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.WriteMode
import config.Configuration
import datastore.WriteState.WriteFailure
import datastore.WriteState.WriteSuccess
import flatMap
import fold
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

interface SimpleDropboxClient {
    fun readFile(filename: String): Result<ErrorDownloadingFileFromDropbox, String>
    fun writeFile(fileContents: String, filename: String): WriteState
}

class HttpSimpleDropboxClient(identifier: String, config: Configuration) : SimpleDropboxClient {
    private val requestConfig: DbxRequestConfig = DbxRequestConfig.newBuilder(identifier).build()
    private val client: DbxClientV2 = DbxClientV2(requestConfig, config.get(KOTLIN_GMAILER_DROPBOX_ACCESS_TOKEN))

    override fun readFile(filename: String): Result<ErrorDownloadingFileFromDropbox, String> {
        val fileDownloaderResult: Result<ErrorDownloadingFileFromDropbox, DbxDownloader<FileMetadata>> = downloaderFor(filename)
        return fileDownloaderResult.flatMap { it.inputStream() }
                                   .fold (
                                      failure = { Failure(it) },
                                      success = { Success(it.reader().readText()) }
                                   )
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
                is IOException -> WriteFailure()
                else                -> throw e
            }
        }
    }

    private fun DbxDownloader<FileMetadata>.inputStream(): Result<ErrorDownloadingFileFromDropbox, InputStream> {
        return try {
            Success(this.inputStream)
        } catch (e: Exception) {
            when (e) {
                is IllegalStateException -> Failure(ErrorDownloadingFileFromDropbox())
                else -> throw e
            }
        }
    }

    private fun downloaderFor(filename: String): Result<ErrorDownloadingFileFromDropbox, DbxDownloader<FileMetadata>> =
            try {
                val metadata = client.files().download(filename)
                Success(metadata)
            } catch (e: Exception) {
                when (e) {
                    is DownloadErrorException,
                    is DbxException -> Failure(ErrorDownloadingFileFromDropbox(filename))
                    else                       -> throw e
                }
            }
}

sealed class WriteState {
    class WriteSuccess : WriteState()
    class WriteFailure : WriteState()
}

class ErrorDownloadingFileFromDropbox(filename: String? = null) : ErrorRetrievingApplicationState {
    override val message = "Error downloading file ${ filename?.let { "$it " } ?: "" }from Dropbox"
}
