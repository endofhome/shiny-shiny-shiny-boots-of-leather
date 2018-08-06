package jobs.NewsletterGmailerJob

import com.github.jknack.handlebars.Handlebars
import config.Configuration
import config.Configurator
import config.RequiredConfig
import config.RequiredConfigItem
import datastore.ApplicationState
import datastore.DropboxDatastore
import datastore.ErrorDownloadingFileFromDropbox
import datastore.FlatFileApplicationStateMetadata
import datastore.HttpDropboxClient
import gmail.AuthorisedGmailProvider
import gmail.Email
import gmail.GmailSecrets
import gmail.HttpGmailClient
import gmail.SimpleGmailClient
import jobs.Job
import jobs.JobCompanion
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_BODY_A
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_BODY_B
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_BCC_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_DROPBOX_ACCESS_TOKEN
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_FROM_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_FROM_FULLNAME
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_GMAIL_ACCESS_TOKEN
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_GMAIL_CLIENT_SECRET
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_GMAIL_REFRESH_TOKEN
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_JOB_NAME
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_RUN_ON_DAYS
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_TO_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_TO_FULLNAME
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_SUBJECT_A
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_SUBJECT_B
import jobs.NewsletterGmailerJob.NewsletterGmailer.Members
import jobs.NewsletterGmailerJob.NewsletterGmailer.NewsletterGmailerStatus
import jobs.NewsletterGmailerJob.NewsletterGmailer.NewsletterGmailerStatus.CLEANING_THIS_WEEK
import jobs.NewsletterGmailerJob.NewsletterGmailer.NewsletterGmailerStatus.NOT_CLEANING_THIS_WEEK
import result.NotAListOfEmailAddresses
import result.Result
import result.Result.Failure
import result.Result.Success
import result.map
import result.orElse
import java.nio.file.Paths
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress

class NewsletterGmailer(private val gmailClient: SimpleGmailClient, private val appStateDatastore: DropboxDatastore<NewsletterGmailerState>, val membersDatastore: DropboxDatastore<Members>, private val config: Configuration): Job {
    override val jobName: String = config.get(NEWSLETTER_GMAILER_JOB_NAME)

    companion object: JobCompanion {

        sealed class NewsletterGmailerConfigItem : RequiredConfigItem {
            object NEWSLETTER_GMAILER_JOB_NAME : NewsletterGmailerConfigItem()
            object NEWSLETTER_GMAILER_GMAIL_CLIENT_SECRET : NewsletterGmailerConfigItem()
            object NEWSLETTER_GMAILER_GMAIL_ACCESS_TOKEN : NewsletterGmailerConfigItem()
            object NEWSLETTER_GMAILER_GMAIL_REFRESH_TOKEN : NewsletterGmailerConfigItem()
            object NEWSLETTER_GMAILER_DROPBOX_ACCESS_TOKEN : NewsletterGmailerConfigItem()
            object NEWSLETTER_GMAILER_RUN_ON_DAYS : NewsletterGmailerConfigItem()
            object NEWSLETTER_GMAILER_FROM_ADDRESS : NewsletterGmailerConfigItem()
            object NEWSLETTER_GMAILER_FROM_FULLNAME : NewsletterGmailerConfigItem()
            object NEWSLETTER_GMAILER_TO_ADDRESS : NewsletterGmailerConfigItem()
            object NEWSLETTER_GMAILER_TO_FULLNAME : NewsletterGmailerConfigItem()
            object NEWSLETTER_GMAILER_BCC_ADDRESS : NewsletterGmailerConfigItem()
            object NEWSLETTER_SUBJECT_A : NewsletterGmailerConfigItem()
            object NEWSLETTER_SUBJECT_B : NewsletterGmailerConfigItem()
            object NEWSLETTER_BODY_A : NewsletterGmailerConfigItem()
            object NEWSLETTER_BODY_B : NewsletterGmailerConfigItem()
        }

        class NewsletterGmailerConfig: RequiredConfig {
            override fun values() = setOf(
                    NEWSLETTER_GMAILER_JOB_NAME,
                    NEWSLETTER_GMAILER_GMAIL_CLIENT_SECRET,
                    NEWSLETTER_GMAILER_GMAIL_ACCESS_TOKEN,
                    NEWSLETTER_GMAILER_GMAIL_REFRESH_TOKEN,
                    NEWSLETTER_GMAILER_DROPBOX_ACCESS_TOKEN,
                    NEWSLETTER_GMAILER_RUN_ON_DAYS,
                    NEWSLETTER_GMAILER_FROM_ADDRESS,
                    NEWSLETTER_GMAILER_FROM_FULLNAME,
                    NEWSLETTER_GMAILER_TO_ADDRESS,
                    NEWSLETTER_GMAILER_TO_FULLNAME,
                    NEWSLETTER_GMAILER_BCC_ADDRESS,
                    NEWSLETTER_SUBJECT_A,
                    NEWSLETTER_SUBJECT_B,
                    NEWSLETTER_BODY_A,
                    NEWSLETTER_BODY_B
            )
        }

        override fun initialise(): NewsletterGmailer {
            val config = Configurator(NewsletterGmailerConfig(), Paths.get("credentials"))
            val gmailSecrets = GmailSecrets(
                    config.get(NEWSLETTER_GMAILER_GMAIL_CLIENT_SECRET),
                    config.get(NEWSLETTER_GMAILER_GMAIL_ACCESS_TOKEN),
                    config.get(NEWSLETTER_GMAILER_GMAIL_REFRESH_TOKEN)
            )
            val gmail = AuthorisedGmailProvider(4000, config.get(NEWSLETTER_GMAILER_JOB_NAME), gmailSecrets, config).gmail()
            val gmailClient = HttpGmailClient(gmail)
            val dropboxClient = HttpDropboxClient(config.get(NEWSLETTER_GMAILER_JOB_NAME), config.get(NEWSLETTER_GMAILER_DROPBOX_ACCESS_TOKEN))
            val appStateMetadata = FlatFileApplicationStateMetadata("/newsletter_gmailer.json", NewsletterGmailerState::class.java)
            val appStateDatastore = DropboxDatastore(dropboxClient, appStateMetadata)
            val membersMetadata = FlatFileApplicationStateMetadata("/members.json", Members::class.java)
            val membersDatastore = DropboxDatastore(dropboxClient, membersMetadata)
            return NewsletterGmailer(gmailClient, appStateDatastore, membersDatastore, config)
        }
    }

    class StateRetriever(private val appStateDatastore: DropboxDatastore<NewsletterGmailerState>, private val membersDatastore: DropboxDatastore<Members>) {
        fun state(): Result<ErrorDownloadingFileFromDropbox, ExternalState> {
            val currentApplicationState = appStateDatastore.currentApplicationState()
            val currentMembers = membersDatastore.currentApplicationState()

            return when {
                currentApplicationState is Failure -> Failure(currentApplicationState.reason)
                currentMembers is Failure          -> Failure(currentMembers.reason)
                else                               -> Success(ExternalState(
                                                        (currentApplicationState as Success).value,
                                                        (currentMembers as Success).value
                                                      ))
            }
        }
    }

    override fun run(now: ZonedDateTime): String =
        StateRetriever(appStateDatastore, membersDatastore).state().map { state: ExternalState ->
            val (appState, members) = state
            when (appState.status) {
                CLEANING_THIS_WEEK     -> sendEmail(cleaningContext.withRecipients(members.allInternetAddresses()), appState.cleaner!!)
                NOT_CLEANING_THIS_WEEK -> sendEmail(notCleaningContext.withRecipients(listOf(appState.nextUp.internetAddress())), appState.nextUp)
            }
        }.orElse { error -> error.message }

    data class Context(val emailSubject: String, val emailBody: String, val successTemplate: String, val recipients: List<InternetAddress> = emptyList()) {
        fun withRecipients(recipients: List<InternetAddress>) = this.copy(recipients = recipients)
    }
    private val cleaningContext = Context(config.get(NEWSLETTER_SUBJECT_A), config.get(NEWSLETTER_BODY_A), "{{this}} is cleaning this week - an email has been sent to all members.\nCurrent state has been stored in Dropbox")
    private val notCleaningContext = Context(config.get(NEWSLETTER_SUBJECT_B), config.get(NEWSLETTER_BODY_B), "There is no cleaning this week - an email reminder has been sent to {{this}} who is cleaning next week.\nCurrent state has been stored in Dropbox")

    private fun sendEmail(context: Context, nextCleaner: Member): String {
        val from = InternetAddress(
                config.get(NEWSLETTER_GMAILER_FROM_ADDRESS),
                config.get(NEWSLETTER_GMAILER_FROM_FULLNAME)
        )
        val to = context.recipients
        val bccResult = config.get(NEWSLETTER_GMAILER_BCC_ADDRESS).toInternetAddresses()
        val bcc = (bccResult as Success).value
        val subject = context.emailSubject
        val body = context.emailBody
        val email = Email(from, to, bcc, subject, body)
        return gmailClient.send(email.toGmailMessage())
                          .map { Handlebars().compileInline(context.successTemplate).apply(nextCleaner.fullname()) }
                          .orElse { "" }
    }

    private fun String.toInternetAddresses(delimiter: Char = ','): Result<NotAListOfEmailAddresses, List<InternetAddress>> =
        try {
            Success(this.split(delimiter).map { InternetAddress(it, true) })
        } catch (e: Exception) {
            when (e) {
                is AddressException -> Failure(NotAListOfEmailAddresses(this))
                else                -> throw e
            }
        }

    enum class NewsletterGmailerStatus {
        CLEANING_THIS_WEEK,
        NOT_CLEANING_THIS_WEEK
    }

    data class Members(val members: List<Member>): ApplicationState {
        fun allInternetAddresses(): List<InternetAddress> = members.map { it.internetAddress() }
    }
}

data class NewsletterGmailerState(
        val status: NewsletterGmailerStatus,
        val cleaner: Member?,
        val nextUp: Member,
        val lastRanOn: LocalDate,
        val emailContents: String
) : ApplicationState

data class Member(val name: String, val surname: String?, val email: String) {

    fun internetAddress(): InternetAddress =
            InternetAddress(email, fullname())
    fun fullname(): String = "$name${surname?.let { " $it" } ?: ""}"
}

data class ExternalState(val appState: NewsletterGmailerState, val members: Members)
