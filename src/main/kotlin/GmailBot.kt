import GmailBot.Companion.RequiredConfig
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_BCC_ADDRESS
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_FROM_ADDRESS
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_FROM_FULLNAME
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_GMAIL_QUERY
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_RUN_ON_DAYS
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_TO_ADDRESS
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_TO_FULLNAME
import Result.Failure
import Result.Success
import config.Configuration
import config.Configurator
import datastore.Datastore
import datastore.DropboxDatastore
import datastore.FlatFileApplicationStateMetadata
import datastore.HttpSimpleDropboxClient
import datastore.SimpleDropboxClient
import datastore.WriteState.WriteFailure
import datastore.WriteState.WriteSuccess
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
    val runOnDays = config.get(KOTLIN_GMAILER_RUN_ON_DAYS).split(",").map { it.trim().toInt() }
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
        val appStateMetadata = FlatFileApplicationStateMetadata("/gmailer_state.json", GmailerState::class.java)
        val datastore: Datastore<GmailerState> = DropboxDatastore(dropboxClient, appStateMetadata)
        val shouldRunNow = daysOfMonthToRun.includes(now.dayOfMonth)

        return shouldRunNow.flatMap { datastore.currentApplicationState() }
                           .flatMap { applicationState: GmailerState -> shouldTryToSend(applicationState, now) }
                           .map { emailBytes -> tryToSendEmail(datastore, emailBytes) }
                           .orElse {it.message }
    }

    private fun shouldTryToSend(applicationState: GmailerState, now: ZonedDateTime): Result<Err, ByteArray> {
        fun ZonedDateTime.yearMonth(): YearMonth = YearMonth.from(this)

        fun thisExactEmailAlreadySent(emailBytes: ByteArray, applicationState: GmailerState): Boolean {
            val separatorBetweenHeaderAndMainContent = "________________________________"
            val newEmailContents = String(emailBytes).substringAfter(separatorBetweenHeaderAndMainContent)
            val previousEmailContents = applicationState.emailContents.substringAfter(separatorBetweenHeaderAndMainContent)
            return newEmailContents.contentEquals(previousEmailContents)
        }

        val gmailQuery = config.get(KOTLIN_GMAILER_GMAIL_QUERY)
        val searchResult = gmailer.lastEmailForQuery(gmailQuery)
        val emailBytes = searchResult?.let {
            gmailer.rawContentOf(searchResult)
        }

        val lastEmailSent = applicationState.lastEmailSent
        return when {
            lastEmailSent > now                                     -> Failure(InvalidStateInFuture())
            searchResult == null                                    -> Failure(NoMatchingResultsForQuery(gmailQuery))
            emailBytes == null                                      -> Failure(CouldNotGetRawContentForEmail())
            thisExactEmailAlreadySent(emailBytes, applicationState) -> Failure(ThisEmailAlreadySent())
            lastEmailSent.yearMonth() == now.yearMonth()            -> Failure(AnEmailAlreadySentThisMonth(now))
            lastEmailSent.yearMonth() < now.yearMonth()             -> Success(emailBytes)
            else                                                    -> Failure(UnknownError())
        }
    }

    private fun tryToSendEmail(datastore: Datastore<GmailerState>, rawMessageToSend: ByteArray): String {
        val fromEmailAddress = config.get(KOTLIN_GMAILER_FROM_ADDRESS)
        val fromFullName = config.get(KOTLIN_GMAILER_FROM_FULLNAME)
        val toEmailAddress = config.get(KOTLIN_GMAILER_TO_ADDRESS)
        val toFullName = config.get(KOTLIN_GMAILER_TO_FULLNAME)
        val bccEmailAddress = config.get(KOTLIN_GMAILER_BCC_ADDRESS)


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
                is WriteSuccess -> "Current state has been stored in Dropbox"
                is WriteFailure -> "Error - could not store state in Dropbox"
            }
        } ?: ""

        val resultMessages = listOf(wasEmailSent, wasStateUpdated).filter { it.isNotBlank() }
        return resultMessages.joinToString("\n")
    }

    private fun List<Int>.includes(dayOfMonth: Int): Result<NoNeedToRunAtThisTime, Int> = when {
        this.contains(dayOfMonth) -> Success(dayOfMonth)
        else                      -> Failure(NoNeedToRunAtThisTime(dayOfMonth, this))
    }
}


sealed class Result<out F, out S> {
    data class Success<out S>(val value: S) : Result<Nothing, S>()
    data class Failure<out F>(val reason: F) : Result<F, Nothing>()
}

fun <F, S, T> Result<F, S>.map(f: (S) -> T): Result<F, T> =
        when (this) {
            is Success<S> -> Success(f(this.value))
            is Failure<F> -> this
        }

fun <F, S, T> Result<F, S>.flatMap(f: (S) -> Result<F, T>): Result<F, T> =
    when (this) {
        is Success<S> -> f(this.value)
        is Failure<F> -> this
    }

fun <F, S, FINAL> Result<F, S>.fold(failure: (F) -> FINAL, success: (S) -> FINAL) : FINAL = this.map(success).orElse(failure)

fun <F, S> Result<F, S>.orElse(f: (F) -> S): S =
        when (this) {
            is Success<S> -> this.value
            is Failure<F> -> f(this.reason)
        }

class NoNeedToRunAtThisTime(dayOfMonth: Int, daysOfMonthToRun: List<Int>) : Err  {
    override val message = "No need to run: day of month is: $dayOfMonth, only running on day ${daysOfMonthToRun.joinToString(", ")} of each month"
}

class InvalidStateInFuture : Err {
    override val message = "Exiting due to invalid state, previous email appears to have been sent in the future"
}

class ThisEmailAlreadySent : Err {
    override val message = "Exiting as this exact email has already been sent"
}

class AnEmailAlreadySentThisMonth(now: ZonedDateTime) : Err {
    override val message = "Exiting, email has already been sent for ${now.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${now.year}"
}

class NoMatchingResultsForQuery(queryString: String) : Err {
    override val message = "No matching results for query: '$queryString'"
}

class CouldNotGetRawContentForEmail : Err {
    override val message = "Error - could not get raw message content for email"
}

class UnknownError : Err {
    override val message = "Exiting due to unknown error"
}

interface Err { val message: String }
