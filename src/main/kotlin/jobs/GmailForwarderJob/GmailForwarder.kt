package jobs.GmailForwarderJob

import com.google.api.services.gmail.model.Message
import config.Configuration
import config.Configurator
import config.RequiredConfig
import config.RequiredConfigList
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
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig.GMAIL_FORWARDER_BCC_ADDRESS
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig.GMAIL_FORWARDER_DROPBOX_ACCESS_TOKEN
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig.GMAIL_FORWARDER_FROM_ADDRESS
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig.GMAIL_FORWARDER_FROM_FULLNAME
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig.GMAIL_FORWARDER_GMAIL_ACCESS_TOKEN
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig.GMAIL_FORWARDER_GMAIL_CLIENT_SECRET
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig.GMAIL_FORWARDER_GMAIL_QUERY
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig.GMAIL_FORWARDER_GMAIL_REFRESH_TOKEN
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig.GMAIL_FORWARDER_JOB_NAME
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig.GMAIL_FORWARDER_RUN_ON_DAYS
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig.GMAIL_FORWARDER_TO_ADDRESS
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig.GMAIL_FORWARDER_TO_FULLNAME
import jobs.Job
import jobs.JobCompanion
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

            sealed class GmailForwarderConfig : RequiredConfig {
                object GMAIL_FORWARDER_JOB_NAME : GmailForwarderConfig()
                object GMAIL_FORWARDER_GMAIL_CLIENT_SECRET : GmailForwarderConfig()
                object GMAIL_FORWARDER_GMAIL_ACCESS_TOKEN : GmailForwarderConfig()
                object GMAIL_FORWARDER_GMAIL_REFRESH_TOKEN : GmailForwarderConfig()
                object GMAIL_FORWARDER_DROPBOX_ACCESS_TOKEN : GmailForwarderConfig()
                object GMAIL_FORWARDER_GMAIL_QUERY : GmailForwarderConfig()
                object GMAIL_FORWARDER_RUN_ON_DAYS : GmailForwarderConfig()
                object GMAIL_FORWARDER_FROM_ADDRESS : GmailForwarderConfig()
                object GMAIL_FORWARDER_FROM_FULLNAME : GmailForwarderConfig()
                object GMAIL_FORWARDER_TO_ADDRESS : GmailForwarderConfig()
                object GMAIL_FORWARDER_TO_FULLNAME : GmailForwarderConfig()
                object GMAIL_FORWARDER_BCC_ADDRESS : GmailForwarderConfig()
            }

            class GmailForwarderConfigList: RequiredConfigList {
                override fun values() = setOf(
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
                )
            }

            override fun initialise(): GmailForwarder {
                val requiredConfig: Set<GmailForwarderConfig> = GmailForwarderConfigList().values()
                val config = Configurator(requiredConfig, Paths.get("credentials"), GmailForwarderConfigList())
                val gmailSecrets = GmailSecrets(
                        config.get(GMAIL_FORWARDER_GMAIL_CLIENT_SECRET),
                        config.get(GMAIL_FORWARDER_GMAIL_ACCESS_TOKEN),
                        config.get(GMAIL_FORWARDER_GMAIL_REFRESH_TOKEN)
                )
                val gmail = AuthorisedGmailProvider(4000, config.get(GMAIL_FORWARDER_JOB_NAME), gmailSecrets, config).gmail()
                val gmailClient = HttpGmailClient(gmail)
                val dropboxClient = HttpDropboxClient(config.get(GMAIL_FORWARDER_JOB_NAME), config.get(GMAIL_FORWARDER_DROPBOX_ACCESS_TOKEN))
                return GmailForwarder(config.get(GMAIL_FORWARDER_JOB_NAME), gmailClient, dropboxClient, config)
            }
        }

    override fun run(now: ZonedDateTime): String {
        val appStateMetadata = FlatFileApplicationStateMetadata("/gmailer_state.json", GmailForwarderState::class.java)
        val datastore: Datastore<GmailForwarderState> = DropboxDatastore(dropboxClient, appStateMetadata)
        val shouldRunNow = config.getAsListOfInt(GMAIL_FORWARDER_RUN_ON_DAYS).includes(now.dayOfMonth)

        return shouldRunNow.flatMap { datastore.currentApplicationState() }
                           .flatMap { applicationState: GmailForwarderState -> shouldTryToSend(applicationState, now) }
                           .map { emailBytes -> tryToSendEmail(datastore, emailBytes) }
                           .orElse { error -> error.message }
    }

    private fun shouldTryToSend(applicationState: GmailForwarderState, now: ZonedDateTime): Result<Err, ByteArray> {
        fun ZonedDateTime.yearMonth(): YearMonth = YearMonth.from(this)

        fun thisExactEmailAlreadySent(emailBytes: ByteArray, applicationState: GmailForwarderState): Boolean {
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

    private fun tryToSendEmail(datastore: Datastore<GmailForwarderState>, rawMessageToSend: ByteArray): String {

        val clonedMessageWithNewHeaders = gmailClient.newMessageFrom(rawMessageToSend).run {
            replaceSender(InternetAddress(config.get(GMAIL_FORWARDER_FROM_ADDRESS), config.get(GMAIL_FORWARDER_FROM_FULLNAME)))
            replaceRecipient(InternetAddress(config.get(GMAIL_FORWARDER_TO_ADDRESS), config.get(GMAIL_FORWARDER_TO_FULLNAME)), RecipientType.TO)
            replaceRecipient(InternetAddress(config.get(GMAIL_FORWARDER_BCC_ADDRESS)), RecipientType.BCC)
            encode()
        }

        return gmailClient.send(clonedMessageWithNewHeaders)
                          .flatMap { message -> message.decodeRawWithResult() }
                          .flatMap { emailContents ->
                              val newState = GmailForwarderState(ZonedDateTime.now(), emailContents)
                              datastore.store(newState, "New email has been sent")
                          }
                          .orElse { error -> error.message }
    }

    private fun Message.decodeRawWithResult() : Result<ErrorDecoding, String> =
            this.decodeRaw()?.let { Success(String(it)) } ?: Failure(ErrorDecoding("message was sent but updated state was not stored in Dropbox."))

    private fun List<Int>.includes(dayOfMonth: Int): Result<NoNeedToRunAtThisTime, Int> = when {
        this.contains(dayOfMonth) -> Success(dayOfMonth)
        else                      -> Failure(NoNeedToRunAtThisTime(dayOfMonth, this))
    }
}

data class GmailForwarderState(val lastEmailSent: ZonedDateTime, val emailContents: String) : ApplicationState
