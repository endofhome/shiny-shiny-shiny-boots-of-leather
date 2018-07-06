import GmailBot.Companion.RequiredConfig
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_BCC_ADDRESS
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_FROM_ADDRESS
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_FROM_FULLNAME
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_GMAIL_QUERY
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_RUN_ON_DAYS
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_TO_ADDRESS
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_TO_FULLNAME
import com.google.api.services.gmail.model.Message
import config.Configuration
import config.Configurator
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
import java.nio.file.Paths
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import javax.mail.Message.RecipientType
import javax.mail.internet.InternetAddress

fun main(args: Array<String>) {
    val requiredConfig: List<RequiredConfig> = RequiredConfig.values().toList()
    val config = Configurator(requiredConfig, Paths.get("credentials"))
    val gmail = AuthorisedGmailProvider(4000, GmailBot.appName, config).gmail()
    val gmailer = HttpGmailer(gmail)
    val dropboxClient = HttpSimpleDropboxClient(GmailBot.appName, config)
    val runOnDays = config[KOTLIN_GMAILER_RUN_ON_DAYS]!!.split(",").map { it.trim().toInt() }
    val result = GmailBot(gmailer, dropboxClient, config).run(ZonedDateTime.now(), runOnDays)
    println(result)
}

class GmailBot(private val gmailer: Gmailer, private val dropboxClient: SimpleDropboxClient, private val config: Configuration) {

    companion object {
        const val appName = "kotlin-gmailer-bot"

        enum class RequiredConfig {
            KOTLIN_GMAILER_GMAIL_CLIENT_SECRET,
            KOTLIN_GMAILER_GMAIL_ACCESS_TOKEN,
            KOTLIN_GMAILER_GMAIL_REFRESH_TOKEN,
            KOTLIN_GMAILER_DROPBOX_ACCESS_TOKEN,
            KOTLIN_GMAILER_GMAIL_QUERY,
            KOTLIN_GMAILER_RUN_ON_DAYS,
            KOTLIN_GMAILER_FROM_ADDRESS,
            KOTLIN_GMAILER_FROM_FULLNAME,
            KOTLIN_GMAILER_TO_ADDRESS,
            KOTLIN_GMAILER_TO_FULLNAME,
            KOTLIN_GMAILER_BCC_ADDRESS
        }
    }

    fun run(now: ZonedDateTime, daysOfMonthToRun: List<Int>): String {
        val gmailQuery = config[KOTLIN_GMAILER_GMAIL_QUERY]!!

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
        val fromEmailAddress = config[KOTLIN_GMAILER_FROM_ADDRESS]!!
        val fromFullName = config[KOTLIN_GMAILER_FROM_FULLNAME]!!
        val toEmailAddress = config[KOTLIN_GMAILER_TO_ADDRESS]!!
        val toFullName = config[KOTLIN_GMAILER_TO_FULLNAME]!!
        val bccEmailAddress = config[KOTLIN_GMAILER_BCC_ADDRESS]!!

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