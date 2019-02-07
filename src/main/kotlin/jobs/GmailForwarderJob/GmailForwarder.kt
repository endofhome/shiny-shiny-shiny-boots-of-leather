package jobs.GmailForwarderJob

import com.google.api.services.gmail.model.Message
import config.Configuration
import config.Configurator
import config.RequiredConfig
import config.stringToInt
import datastore.ApplicationState
import datastore.Datastore
import datastore.DropboxDatastore
import datastore.FlatFileApplicationStateMetadata
import datastore.HttpDropboxClient
import datastore.SimpleDropboxClient
import gmail.AuthorisedGmailProvider
import gmail.GmailSecrets
import gmail.HttpGmailClient
import gmail.SimpleGmailClient
import gmail.encode
import gmail.replaceRecipient
import gmail.replaceSender
import jobs.GmailForwarderJob.GmailForwarderConfigItem.BCC_ADDRESS
import jobs.GmailForwarderJob.GmailForwarderConfigItem.DROPBOX_ACCESS_TOKEN
import jobs.GmailForwarderJob.GmailForwarderConfigItem.FROM_ADDRESS
import jobs.GmailForwarderJob.GmailForwarderConfigItem.FROM_FULLNAME
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_ACCESS_TOKEN
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_CLIENT_SECRET
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_QUERY
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_REFRESH_TOKEN
import jobs.GmailForwarderJob.GmailForwarderConfigItem.RUN_ON_DAYS
import jobs.GmailForwarderJob.GmailForwarderConfigItem.TO_ADDRESS
import jobs.GmailForwarderJob.GmailForwarderConfigItem.TO_FULLNAME
import jobs.Job
import jobs.JobCompanion
import result.AnEmailAlreadySentThisMonth
import result.CouldNotGetRawContentForEmail
import result.Err
import result.ErrorDecoding
import result.InvalidStateInFuture
import result.NoMatchingResultsForQuery
import result.NoNeedToRunOnThisDayOfMonth
import result.NonMultipartEmail
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
import java.util.Base64
import javax.mail.Message.RecipientType.BCC
import javax.mail.Message.RecipientType.TO
import javax.mail.internet.InternetAddress

class GmailForwarder(private val gmailClient: SimpleGmailClient, private val dropboxClient: SimpleDropboxClient, private val config: Configuration): Job {
    override val jobName = config.requiredConfig.formattedJobName

    companion object: JobCompanion {

        override fun initialise(requiredConfig: RequiredConfig): GmailForwarder {
            val jobName = requiredConfig.formattedJobName
            val config = Configurator(requiredConfig, Paths.get("credentials"))
            val gmailSecrets = GmailSecrets(
                    config.get(GMAIL_CLIENT_SECRET(jobName)),
                    config.get(GMAIL_ACCESS_TOKEN(jobName)),
                    config.get(GMAIL_REFRESH_TOKEN(jobName))
            )
            val gmail = AuthorisedGmailProvider(4000, jobName.value, gmailSecrets, config).gmail()
            val gmailClient = HttpGmailClient(gmail)
            val dropboxClient = HttpDropboxClient(jobName.value, config.get(DROPBOX_ACCESS_TOKEN((jobName))))
            return GmailForwarder(gmailClient, dropboxClient, config)
        }

        fun encodeBase64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray())
        fun decodeBase64(s: String): String = String(Base64.getDecoder().decode(s))
    }

    override fun run(now: ZonedDateTime): String {
        val appStateMetadata = FlatFileApplicationStateMetadata("/gmailer_state.json", GmailForwarderState::class.java)
        val datastore: Datastore<GmailForwarderState> = DropboxDatastore(dropboxClient, appStateMetadata)
        val runOnDays: List<Int> = config.getAsListOf(RUN_ON_DAYS(jobName), stringToInt)
        val shouldRunNow = runOnDays.includes(now.dayOfMonth)

        return shouldRunNow.flatMap { datastore.currentApplicationState() }
                           .flatMap { applicationState: GmailForwarderState -> shouldTryToSend(applicationState, now) }
                           .map { emailBytes -> tryToSendEmail(datastore, emailBytes) }
                           .orElse { error -> error.message }
    }

    private fun shouldTryToSend(applicationState: GmailForwarderState, now: ZonedDateTime): Result<Err, ByteArray> {
        fun ZonedDateTime.yearMonth(): YearMonth = YearMonth.from(this)

        fun nonMultipartEmail(emailBytes: ByteArray): Boolean = String(emailBytes).contains("Content-Type: multipart/").not()

        fun thisExactEmailAlreadySent(emailBytes: ByteArray, applicationState: GmailForwarderState): Boolean {
            val emailString = String(emailBytes)
            val newEmailContents = emailString.contentWithoutHeaders().trim()
            val previousEmailContents = decodeBase64(applicationState.emailContents).trim()
            return newEmailContents.contentEquals(previousEmailContents)
        }

        val gmailQuery = config.get(GMAIL_QUERY(jobName))
        val searchResult = gmailClient.lastEmailForQuery(gmailQuery)
        val emailBytes = searchResult?.let {
            gmailClient.rawContentOf(searchResult)
        }

        val lastEmailSent = applicationState.lastEmailSent
        return when {
            lastEmailSent > now                                     -> Failure(InvalidStateInFuture())
            searchResult == null                                    -> Failure(NoMatchingResultsForQuery(gmailQuery))
            emailBytes == null                                      -> Failure(CouldNotGetRawContentForEmail())
            nonMultipartEmail(emailBytes)                           -> Failure(NonMultipartEmail())
            thisExactEmailAlreadySent(emailBytes, applicationState) -> Failure(ThisEmailAlreadySent())
            lastEmailSent.yearMonth() == now.yearMonth()            -> Failure(AnEmailAlreadySentThisMonth(now))
            lastEmailSent.yearMonth() < now.yearMonth()             -> Success(emailBytes)
            else                                                    -> Failure(UnknownError())
        }
    }

    private fun tryToSendEmail(datastore: Datastore<GmailForwarderState>, rawMessageToSend: ByteArray): String {
        val recipient = InternetAddress(config.get(TO_ADDRESS(jobName)), config.get(TO_FULLNAME(jobName)))
        val clonedMessageWithNewHeaders = gmailClient.newMessageFrom(rawMessageToSend).run {
            replaceSender(InternetAddress(config.get(FROM_ADDRESS(jobName)), config.get(FROM_FULLNAME(jobName))))
            replaceRecipient(recipient, TO)
            replaceRecipient(InternetAddress(config.get(BCC_ADDRESS(jobName))), BCC)
            encode()
        }

        val subject = String(clonedMessageWithNewHeaders.decodeRaw()).substringAfter("Subject: ").substringBefore("\r\n")
        return gmailClient.send(clonedMessageWithNewHeaders, subject, listOf(recipient))
                          .flatMap { message -> message.decodeRawWithResult() }
                          .flatMap { emailContents ->
                              val newState = GmailForwarderState(ZonedDateTime.now(), encodeBase64(emailContents))
                              datastore.store(newState, "New email has been sent")
                          }
                          .orElse { error -> error.message }
    }

    private fun Message.decodeRawWithResult() : Result<ErrorDecoding, String> =
        this.decodeRaw()?.let { messageBytes ->
            Success(String(messageBytes).contentWithoutHeaders())
        } ?: Failure(ErrorDecoding("message was sent but updated state was not stored in Dropbox."))

    private fun List<Int>.includes(dayOfMonth: Int): Result<NoNeedToRunOnThisDayOfMonth, Int> = when {
        this.contains(dayOfMonth) -> Success(dayOfMonth)
        else                      -> Failure(NoNeedToRunOnThisDayOfMonth(dayOfMonth, this))
    }

    private fun String.contentWithoutHeaders(): String =
        this.substringAfter("Content-Type: multipart/")
            .substringAfter("boundary=")
            .substringBefore("\n")
            .removeSurrounding("\"").let { boundary ->
                this.substringAfter(boundary)
                    .substringAfter(boundary)
                    .replace(boundary, "_____________________________________________________________")
        }
}

data class GmailForwarderState(val lastEmailSent: ZonedDateTime, val emailContents: String) : ApplicationState
