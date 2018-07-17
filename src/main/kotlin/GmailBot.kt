import GmailBot.Companion.RequiredConfig
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_BCC_ADDRESS
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_DROPBOX_ACCESS_TOKEN
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_FROM_ADDRESS
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_FROM_FULLNAME
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_GMAIL_QUERY
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_RUN_ON_DAYS
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_TO_ADDRESS
import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_TO_FULLNAME
import GmailBot.Companion.RequiredConfig.values
import com.google.api.services.gmail.model.Message
import config.Configuration
import config.Configurator
import datastore.Datastore
import datastore.DropboxDatastore
import datastore.FlatFileApplicationStateMetadata
import datastore.HttpDropboxClient
import datastore.SimpleDropboxClient
import gmail.AuthorisedGmailProvider
import gmail.GmailerState
import gmail.HttpGmailClient
import gmail.SimpleGmailClient
import gmail.encode
import gmail.replaceRecipient
import gmail.replaceSender
import result.AnEmailAlreadySentThisMonth
import result.CouldNotGetRawContentForEmail
import result.Err
import result.ErrorDecoding
import result.InvalidStateInFuture
import result.NoMatchingResultsForQuery
import result.NoNeedToRunAtThisTime
import result.Result
import result.Result.Failure
import result.Result.Success
import result.ThisEmailAlreadySent
import result.UnknownError
import result.flatMap
import result.map
import result.orElse
import java.nio.file.Paths
import java.time.YearMonth
import java.time.ZonedDateTime
import javax.mail.Message.RecipientType
import javax.mail.internet.InternetAddress

fun main(args: Array<String>) {
    val requiredConfig: List<RequiredConfig> = values().toList()
    val config = Configurator(requiredConfig, Paths.get("credentials"))
    val gmail = AuthorisedGmailProvider(4000, GmailBot.appName, config).gmail()
    val gmailer = HttpGmailClient(gmail)
    val dropboxClient = HttpDropboxClient(GmailBot.appName, config.get(KOTLIN_GMAILER_DROPBOX_ACCESS_TOKEN))
    val runOnDays = config.getAsListOf<Int>(KOTLIN_GMAILER_RUN_ON_DAYS)
    val result = GmailBot(gmailer, dropboxClient, config).run(ZonedDateTime.now(),  runOnDays)
    println(result)
}

class GmailBot(private val gmailClient: SimpleGmailClient, private val dropboxClient: SimpleDropboxClient, private val config: Configuration) {

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
                           .orElse { error -> error.message }
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
        val searchResult = gmailClient.lastEmailForQuery(gmailQuery)
        val emailBytes = searchResult?.let {
            gmailClient.rawContentOf(searchResult)
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

        val clonedMessageWithNewHeaders = gmailClient.newMessageFrom(rawMessageToSend).run {
            replaceSender(InternetAddress(fromEmailAddress, fromFullName))
            replaceRecipient(InternetAddress(toEmailAddress, toFullName), RecipientType.TO)
            replaceRecipient(InternetAddress(bccEmailAddress), RecipientType.BCC)
            encode()
        }

        return gmailClient.send(clonedMessageWithNewHeaders)
                          .flatMap { message -> message.decodeRawWithResult() }
                          .flatMap { emailContents ->
                              val newState = GmailerState(ZonedDateTime.now(), emailContents)
                              datastore.store(newState, "New email has been sent")
                          }
                          .orElse { error -> error.message }
    }

    private fun Message.decodeRawWithResult() : Result<ErrorDecoding, String> =
            this.decodeRaw()?.let { Success(String(it)) } ?: Failure(ErrorDecoding())

    private fun List<Int>.includes(dayOfMonth: Int): Result<NoNeedToRunAtThisTime, Int> = when {
        this.contains(dayOfMonth) -> Success(dayOfMonth)
        else                      -> Failure(NoNeedToRunAtThisTime(dayOfMonth, this))
    }
}
