import com.google.api.services.gmail.model.Message
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import javax.mail.internet.InternetAddress

class GmailBot(private val gmailer: Gmailer, private val dropboxClient: SimpleDropboxClient) {

    companion object {
        const val appName = "kotlin-gmailer-bot"
    }

    fun run(now: ZonedDateTime, daysOfMonthToRun: List<Int>): String {
        val gmailQuery = System.getenv("KOTLIN_GMAILER_GMAIL_QUERY") ?: ""

        val appStateMetadata = FlatFileApplicationStateMetadata("/gmailer_state", GmailerState::class.java)
        val datastore: Datastore<GmailerState> = DropboxDatastore(dropboxClient, appStateMetadata)
        val dayOfMonth = now.dayOfMonth

        if (daysOfMonthToRun.contains(dayOfMonth).not()) {
            return("No need to run: day of month is: $dayOfMonth, only running on day ${daysOfMonthToRun.joinToString(", ")} of each month")
        }

        val applicationState = datastore.currentApplicationState().state
        val firstMatch: Message? = gmailer.lastEmailForQuery(gmailQuery)
        val emailBytes = firstMatch?.let {
             gmailer.rawMessageContent(firstMatch)
        }

        val lastEmailSent = applicationState.lastEmailSent
        val state = when {
            lastEmailSent > now                                                            -> State.INVALID_STATE_IN_FUTURE
            lastEmailSent.yearMonth() == now.yearMonth()                                   -> State.AN_EMAIL_ALREADY_SENT_THIS_MONTH
            emailBytes != null && thisExactEmailAlreadySent(emailBytes, applicationState)  -> State.THIS_EMAIL_ALREADY_SENT
            lastEmailSent.yearMonth() < now.yearMonth()                                    -> State.NO_EMAIL_SENT_THIS_MONTH
            else                                                                           -> State.UNKNOWN_ERROR
        }

        return when (state) {
            State.THIS_EMAIL_ALREADY_SENT          -> "Exiting as this exact email has already been sent"
            State.AN_EMAIL_ALREADY_SENT_THIS_MONTH -> "Exiting, email has already been sent for ${now.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${now.year}"
            State.UNKNOWN_ERROR                    -> "Exiting due to unknown error"
            State.INVALID_STATE_IN_FUTURE          -> "Exiting due to invalid state, previous email appears to have been sent in the future"
            State.NO_EMAIL_SENT_THIS_MONTH         -> tryToSendEmail(datastore, emailBytes)
        }
    }

    private fun thisExactEmailAlreadySent(emailBytes: ByteArray, applicationState: GmailerState) =
            emailBytes.contentEquals(applicationState.emailContents.toByteArray())

    private fun tryToSendEmail(datastore: Datastore<GmailerState>, rawMessageToSend: ByteArray?): String {
        val fromEmailAddress = System.getenv("KOTLIN_GMAILER_FROM_ADDRESS") ?: ""
        val fromFullName = System.getenv("KOTLIN_GMAILER_FROM_FULLNAME") ?: ""
        val toEmailAddress = System.getenv("KOTLIN_GMAILER_TO_ADDRESS") ?: ""
        val toFullName = System.getenv("KOTLIN_GMAILER_TO_FULLNAME") ?: ""

        rawMessageToSend?.let {
            val clonedMessage = gmailer.newMessageFrom(rawMessageToSend)
                    .withSender(InternetAddress(fromEmailAddress, fromFullName))
                    .withRecipient(InternetAddress(toEmailAddress, toFullName))
                    .encode()
        gmailer.send(clonedMessage)

            // TODO test case, not checking whether it worked or not...
            val wasEmailSent = "New email has been sent"

            val emailContents = String(clonedMessage.decodeRaw())
            val newState = ApplicationState(GmailerState(ZonedDateTime.now(), emailContents))
            datastore.store(newState)

            // TODO test case, not checking whether it worked or not...
            val wasStateUpdated = "Current state has been stored in Dropbox"
            return "$wasEmailSent\n$wasStateUpdated"
        }

        // TODO test case
        return "Couldn't find any relevant emails to send."
    }

    private fun ZonedDateTime.yearMonth(): YearMonth = YearMonth.from(this)
}

enum class State {
    NO_EMAIL_SENT_THIS_MONTH,
    AN_EMAIL_ALREADY_SENT_THIS_MONTH,
    THIS_EMAIL_ALREADY_SENT,
    INVALID_STATE_IN_FUTURE,
    UNKNOWN_ERROR
}