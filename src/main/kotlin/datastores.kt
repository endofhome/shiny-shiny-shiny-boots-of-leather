import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.YearMonth
import java.time.ZonedDateTime


enum class State {
    NEW, EMAIL_SENT, UNKNOWN_ERROR
}

interface Datastore {
    fun applicationStateFor(date: ZonedDateTime): State
    fun store(state: GmailerState)
}

class DropboxDatastore(appName: String, accessToken: String)  : Datastore {
    private val simpleDropboxClient = SimpleDropboxClient(appName, accessToken)

    private val objectMapper = ObjectMapper().registerKotlinModule()
                                             .registerModule(JavaTimeModule())
    private val stateFilename = "/gmailer_state"

    override fun applicationStateFor(date: ZonedDateTime): State {
        val appStateFileContents = simpleDropboxClient.readFile(stateFilename)
        val applicationState = objectMapper.readValue(appStateFileContents, GmailerState::class.java)

        return when {
            applicationState.lastEmailSent.yearMonth() < date.yearMonth() -> State.NEW
            applicationState.lastEmailSent.yearMonth() == date.yearMonth() -> State.EMAIL_SENT
            else -> State.UNKNOWN_ERROR
        }
    }

    override fun store(state: GmailerState) {
        val fileContents = objectMapper.writeValueAsString(state)
        simpleDropboxClient.writeFile(fileContents, stateFilename)
    }

    private fun ZonedDateTime.yearMonth(): YearMonth = YearMonth.from(this)
}
