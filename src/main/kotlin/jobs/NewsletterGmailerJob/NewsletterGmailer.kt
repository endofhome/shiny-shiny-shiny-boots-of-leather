package jobs.NewsletterGmailerJob

import config.Configuration
import config.Configurator
import config.RequiredConfig
import config.RequiredConfigItem
import datastore.ApplicationState
import datastore.DropboxDatastore
import datastore.FlatFileApplicationStateMetadata
import datastore.HttpDropboxClient
import datastore.SimpleDropboxClient
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
import jobs.NewsletterGmailerJob.NewsletterGmailerStatus.CLEANING_THIS_WEEK
import jobs.NewsletterGmailerJob.NewsletterGmailerStatus.NOT_CLEANING_THIS_WEEK
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

class NewsletterGmailer(private val gmailClient: SimpleGmailClient, private val dropboxClient: SimpleDropboxClient, private val config: Configuration): Job {
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
            return NewsletterGmailer(gmailClient, dropboxClient, config)
        }
    }

    override fun run(now: ZonedDateTime): String {
        val appStateMetadata = FlatFileApplicationStateMetadata("/newsletter_gmailer.json", NewsletterGmailerState::class.java)
        val appStateDatastore = DropboxDatastore(dropboxClient, appStateMetadata)
        val currentApplicationState = appStateDatastore.currentApplicationState()

        val membersMetadata = FlatFileApplicationStateMetadata("/members.json", Members::class.java)
        val membersDatastore = DropboxDatastore(dropboxClient, membersMetadata)
        val currentMembers = membersDatastore.currentApplicationState()

        val successfulAppState = currentApplicationState as Success
        val successfulMembers = currentMembers as Success
        return when (successfulAppState.value.status) {
            CLEANING_THIS_WEEK     -> sendCleaningNotice(successfulMembers.value)
            NOT_CLEANING_THIS_WEEK -> sendRotaReminder(successfulMembers.value)
        }
    }

    private fun sendCleaningNotice(members: Members): String {
        val from = InternetAddress(
                config.get(NEWSLETTER_GMAILER_FROM_ADDRESS),
                config.get(NEWSLETTER_GMAILER_FROM_FULLNAME)
        )
        val to = members.allInternetAddresses()
        val bccResult = config.get(NEWSLETTER_GMAILER_BCC_ADDRESS).toInternetAddresses()
        val bcc = (bccResult as Success).value
        val subject = config.get(NEWSLETTER_SUBJECT_A)
        val body = config.get(NEWSLETTER_BODY_A)
        val email = Email(from, to, bcc, subject, body)
        return gmailClient.send(email.toGmailMessage())
                .map { "Milford is cleaning this week - an email has been sent to all members.\nCurrent state has been stored in Dropbox" }
                .orElse { "" }
    }

    private fun sendRotaReminder(members: Members): String {
    val from = InternetAddress(
            config.get(NEWSLETTER_GMAILER_FROM_ADDRESS),
            config.get(NEWSLETTER_GMAILER_FROM_FULLNAME)
    )
    val to = members.allInternetAddresses()
    val bccResult = config.get(NEWSLETTER_GMAILER_BCC_ADDRESS).toInternetAddresses()
    val bcc = (bccResult as Success).value
    val subject = config.get(NEWSLETTER_SUBJECT_B)
    val body = config.get(NEWSLETTER_BODY_B)
    val email = Email(from, to, bcc, subject, body)
    return gmailClient.send(email.toGmailMessage())
                      .map { "There is no cleaning this week - an email reminder has been sent to Carla who is cleaning next week.\nCurrent state has been stored in Dropbox" }
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
            InternetAddress(email, "$name${surname?.let { " $it" } ?: ""}")
}

data class Members(val members: List<Member>): ApplicationState {
    fun allInternetAddresses(): List<InternetAddress> = members.map { it.internetAddress() }
}

enum class NewsletterGmailerStatus {
    CLEANING_THIS_WEEK,
    NOT_CLEANING_THIS_WEEK
}
