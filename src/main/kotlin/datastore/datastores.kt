package datastore

import Err
import Result
import Result.Failure
import Result.Success
import com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import fold
import gmail.ApplicationState

interface Datastore<T : ApplicationState> {
    fun currentApplicationState(): Result<Err, T>
    fun store(state: T): WriteState
}

class DropboxDatastore<T : ApplicationState>(private val dropboxClient: SimpleDropboxClient, private val appStateMetadata: FlatFileApplicationStateMetadata<T>)  : Datastore<T> {
    private val objectMapper = ObjectMapper().registerKotlinModule()
                                             .registerModule(JavaTimeModule())
                                             .configure(ALLOW_UNQUOTED_CONTROL_CHARS, true)

    override fun currentApplicationState(): Result<ErrorDownloadingFileFromDropbox, T> {
        val appStateFileContents = dropboxClient.readFile(appStateMetadata.filename)
        return appStateFileContents.fold(
                success = { Success(objectMapper.readValue(it, appStateMetadata.stateClass)) },
                failure = { Failure(it) }
        )
    }

    override fun store(state: T): WriteState {
        val fileContents = objectMapper.writeValueAsString(state)
        return dropboxClient.writeFile(fileContents, appStateMetadata.filename)
    }
}

data class FlatFileApplicationStateMetadata<T>(val filename: String, val stateClass: Class<T>)
