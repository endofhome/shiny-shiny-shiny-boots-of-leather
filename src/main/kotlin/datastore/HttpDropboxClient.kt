package datastore

import com.dropbox.core.DbxApiException
import com.dropbox.core.DbxDownloader
import com.dropbox.core.DbxException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.DownloadErrorException
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.WriteMode
import result.Err
import result.Result
import result.Result.Failure
import result.Result.Success
import result.flatMap
import result.fold
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

interface SimpleDropboxClient {
    fun readFile(filename: String): Result<ErrorDownloadingFileFromDropbox, String>
    fun writeFile(fileContents: String, filename: String, fileDescription: String): Result<DropboxWriteFailure, String>
}

class HttpDropboxClient(identifier: String, accessToken: String) : SimpleDropboxClient {
    private val requestConfig: DbxRequestConfig = DbxRequestConfig.newBuilder(identifier).build()
    private val client: DbxClientV2 = DbxClientV2(requestConfig, accessToken)

    override fun readFile(filename: String): Result<ErrorDownloadingFileFromDropbox, String> {
        return downloaderFor(filename).flatMap { it.inputStream() }
                                      .fold (
                                         failure = { Failure(it) },
                                         success = { Success(it.reader().readText()) }
                                      )
    }

    override fun writeFile(fileContents: String, filename: String, fileDescription: String): Result<DropboxWriteFailure, String> {
        return try {
            ByteArrayInputStream(fileContents.toByteArray()).use { inputStream ->
                client.files().uploadBuilder(filename)
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(inputStream)
            }
            Success("$fileDescription\nCurrent state has been stored in Dropbox")
        } catch (e: Exception) {
            when (e) {
                is DbxApiException,
                is DbxException,
                is IOException      -> Failure(DropboxWriteFailure(fileDescription))
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

class ErrorDownloadingFileFromDropbox(filename: String? = null) : Err {
    override val message = "Error downloading file ${ filename?.let { "$it " } ?: "" }from Dropbox"
}

class DropboxWriteFailure(stateDescription: String) : Err {
    override val message = "$stateDescription\nError - could not store state in Dropbox"
}
