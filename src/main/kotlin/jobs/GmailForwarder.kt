package jobs

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
import jobs.GmailForwarder.Companion.RequiredConfig.GMAIL_FORWARDER_BCC_ADDRESS
import jobs.GmailForwarder.Companion.RequiredConfig.GMAIL_FORWARDER_DROPBOX_ACCESS_TOKEN
import jobs.GmailForwarder.Companion.RequiredConfig.GMAIL_FORWARDER_FROM_ADDRESS
import jobs.GmailForwarder.Companion.RequiredConfig.GMAIL_FORWARDER_FROM_FULLNAME
import jobs.GmailForwarder.Companion.RequiredConfig.GMAIL_FORWARDER_GMAIL_QUERY
import jobs.GmailForwarder.Companion.RequiredConfig.GMAIL_FORWARDER_JOB_NAME
import jobs.GmailForwarder.Companion.RequiredConfig.GMAIL_FORWARDER_RUN_ON_DAYS
import jobs.GmailForwarder.Companion.RequiredConfig.GMAIL_FORWARDER_TO_ADDRESS
import jobs.GmailForwarder.Companion.RequiredConfig.GMAIL_FORWARDER_TO_FULLNAME
import jobs.GmailForwarder.Companion.RequiredConfig.values
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

class GmailForwarder(override val jobName: String, private val gmailClient: SimpleGmailClient, private val dropboxClient: SimpleDropboxClient, private val config: Configuration): Job {

        companion object: JobCompanion {

            enum class RequiredConfig {
                GMAIL_FORWARDER_JOB_NAME,
                GMAIL_FORWARDER_GMAIL_CLIENT_SECRET,
                GMAIL_FORWARDER_GMAIL_ACCESS_TOKEN,
                GMAIL_FORWARDER_GMAIL_REFRESH_TOKEN,
                GMAIL_FORWARDER_DROPBOX_ACCESS_TOKEN,
                GMAIL_FORWARDER_GMAIL_QUERY,
                GMAIL_FORWARDER_RUN_ON_DAYS,
                GMAIL_FORWARDER_FROM_ADDRESS,
                GMAIL_FORWARDER_FROM_FULLNAME,
                GMAIL_FORWARDER_TO_ADDRESS,
                GMAIL_FORWARDER_TO_FULLNAME,
                GMAIL_FORWARDER_BCC_ADDRESS
            }

            override fun initialise(): GmailForwarder {
                val requiredConfig: List<GmailForwarder.Companion.RequiredConfig> = values().toList()
                val config = Configurator(requiredConfig, Paths.get("credentials"))
                val gmail = AuthorisedGmailProvider(4000, config.get(GMAIL_FORWARDER_JOB_NAME), config).gmail()
                val gmailer = HttpGmailClient(gmail)
                val dropboxClient = HttpDropboxClient(config.get(GMAIL_FORWARDER_JOB_NAME), config.get(GMAIL_FORWARDER_DROPBOX_ACCESS_TOKEN))
                return GmailForwarder(config.get(GMAIL_FORWARDER_JOB_NAME), gmailer, dropboxClient, config)
            }
        }

    override fun run(now: ZonedDateTime): String {
        val appStateMetadata = FlatFileApplicationStateMetadata("/gmailer_state.json", GmailerState::class.java)
        val datastore: Datastore<GmailerState> = DropboxDatastore(dropboxClient, appStateMetadata)
        val shouldRunNow = config.getAsListOfInt(GMAIL_FORWARDER_RUN_ON_DAYS).includes(now.dayOfMonth)

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

        val gmailQuery = config.get(GMAIL_FORWARDER_GMAIL_QUERY)
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

        val clonedMessageWithNewHeaders = gmailClient.newMessageFrom(rawMessageToSend).run {
            replaceSender(InternetAddress(config.get(GMAIL_FORWARDER_FROM_ADDRESS), config.get(GMAIL_FORWARDER_FROM_FULLNAME)))
            replaceRecipient(InternetAddress(config.get(GMAIL_FORWARDER_TO_ADDRESS), config.get(GMAIL_FORWARDER_TO_FULLNAME)), RecipientType.TO)
            replaceRecipient(InternetAddress(config.get(GMAIL_FORWARDER_BCC_ADDRESS)), RecipientType.BCC)
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
