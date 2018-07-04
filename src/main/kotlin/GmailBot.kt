import com.google.api.services.gmail.model.Message
import datastore.Datastore
import datastore.DropboxDatastore
import datastore.FlatFileApplicationStateMetadata
import datastore.HttpSimpleDropboxClient
import datastore.SimpleDropboxClient
import datastore.WriteState.Failure
import datastore.WriteState.Success
import gmail.AuthorisedGmailProvider
import gmail.Gmailer
import gmail.GmailerState
import gmail.HttpGmailer
import gmail.encode
import gmail.replaceRecipient
import gmail.replaceSender
import java.io.File
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import javax.mail.Message.RecipientType
import javax.mail.internet.InternetAddress

fun main(args: Array<String>) {
    val gmail = AuthorisedGmailProvider(4000, GmailBot.appName).gmail()
    val gmailer = HttpGmailer(gmail)
    val dropboxAccessToken = File("credentials/access_token").readText()
    val dropboxClient = HttpSimpleDropboxClient(GmailBot.appName, dropboxAccessToken)
    val result = GmailBot(gmailer, dropboxClient).run(ZonedDateTime.now(), listOf(1))
    println(result)
}

class GmailBot(private val gmailer: Gmailer, private val dropboxClient: SimpleDropboxClient) {

    companion object {
        const val appName = "kotlin-gmailer-bot"
    }

    fun run(now: ZonedDateTime, daysOfMonthToRun: List<Int>): String {
        val gmailQuery = System.getenv("KOTLIN_GMAILER_GMAIL_QUERY") ?: ""

        val appStateMetadata = FlatFileApplicationStateMetadata("/gmailer_state.json", GmailerState::class.java)
        val datastore: Datastore<GmailerState> = DropboxDatastore(dropboxClient, appStateMetadata)
        val dayOfMonth = now.dayOfMonth

        if (daysOfMonthToRun.contains(dayOfMonth).not()) {
            return("No need to run: day of month is: $dayOfMonth, only running on day ${daysOfMonthToRun.joinToString(", ")} of each month")
        }

        val applicationState = datastore.currentApplicationState()
        val searchResult: Message? = gmailer.lastEmailForQuery(gmailQuery)
        val emailBytes = searchResult?.let {
             gmailer.rawContentOf(searchResult)
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
        val bccEmailAddress = System.getenv("KOTLIN_GMAILER_BCC_ADDRESS") ?: ""

        rawMessageToSend?.let {
            val clonedMessage = gmailer.newMessageFrom(rawMessageToSend)
            val clonedMessageWithNewHeader = clonedMessage?.run {
                    replaceSender(InternetAddress(fromEmailAddress, fromFullName))
                    replaceRecipient(InternetAddress(toEmailAddress, toFullName), RecipientType.TO)
                    replaceRecipient(InternetAddress(bccEmailAddress), RecipientType.BCC)
                    encode()
            }

            val gmailResponse = clonedMessageWithNewHeader?.let { gmailer.send(clonedMessageWithNewHeader) }

            val dropboxState = gmailResponse?.let {
                val emailContents = clonedMessageWithNewHeader.decodeRaw()?.let { String(it) }
                val newState = emailContents?.let { GmailerState(ZonedDateTime.now(), emailContents) }
                newState?.let { datastore.store(newState) }
            }

            val wasEmailSent = gmailResponse?.let {
                "New email has been sent"
            } ?: "Error - could not send email/s"

            val wasStateUpdated = dropboxState?.let {
                when (it) {
                    is Success -> "Current state has been stored in Dropbox"
                    is Failure -> "Error - could not store state in Dropbox"
                }
            } ?: ""

            val resultMessages = listOf(wasEmailSent, wasStateUpdated).filter { it.isNotBlank() }
            return resultMessages.joinToString("\n")
        }

        return "Error - could not get raw message content for email"
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