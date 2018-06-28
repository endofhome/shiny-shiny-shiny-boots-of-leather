package datastore

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import gmail.ApplicationState

interface Datastore<T : ApplicationState> {
    fun currentApplicationState(): T
    fun store(state: T): WriteState
}

class DropboxDatastore<T : ApplicationState>(private val dropboxClient: SimpleDropboxClient, private val appStateMetadata: FlatFileApplicationStateMetadata<T>)  : Datastore<T> {
    private val objectMapper = ObjectMapper().registerKotlinModule()
                                             .registerModule(JavaTimeModule())

    override fun currentApplicationState(): T {
        val appStateFileContents = dropboxClient.readFile(appStateMetadata.filename)
        return objectMapper.readValue(appStateFileContents, appStateMetadata.stateClass)
    }

    override fun store(state: T): WriteState {
        val fileContents = objectMapper.writeValueAsString(state)
        return dropboxClient.writeFile(fileContents, appStateMetadata.filename)
    }
}

data class FlatFileApplicationStateMetadata<T>(val filename: String, val stateClass: Class<T>)
