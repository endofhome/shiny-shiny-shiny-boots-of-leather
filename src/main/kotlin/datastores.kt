import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

interface Datastore<T> {
    fun currentApplicationState(): ApplicationState<T>
    fun store(state: ApplicationState<T>)
}

class DropboxDatastore<T>(private val dropboxClient: SimpleDropboxClient, private val appStateMetadata: FlatFileApplicationStateMetadata<T>)  : Datastore<T> {
    private val objectMapper = ObjectMapper().registerKotlinModule()
                                             .registerModule(JavaTimeModule())

    override fun currentApplicationState(): ApplicationState<T> {
        val appStateFileContents = dropboxClient.readFile(appStateMetadata.filename)
        val t = objectMapper.readValue(appStateFileContents, appStateMetadata.stateClass)
        return ApplicationState(t)
    }

    override fun store(state: ApplicationState<T>) {
        val fileContents = objectMapper.writeValueAsString(state)
        dropboxClient.writeFile(fileContents, appStateMetadata.filename)
    }
}

data class FlatFileApplicationStateMetadata<T>(val filename: String, val stateClass: Class<T>)
