package jobs.NewsletterGmailerJob

import config.Configuration
import config.Configurator
import config.RequiredConfig
import config.RequiredConfigItem
import datastore.ApplicationState
import datastore.HttpDropboxClient
import datastore.SimpleDropboxClient
import gmail.AuthorisedGmailProvider
import gmail.GmailSecrets
import gmail.HttpGmailClient
import gmail.SimpleGmailClient
import jobs.Job
import jobs.JobCompanion
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
import java.nio.file.Paths
import java.time.ZonedDateTime

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
                    NEWSLETTER_GMAILER_BCC_ADDRESS
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
        return "Milford is cleaning this week - an email has been sent to all members.\nCurrent state has been stored in Dropbox"
    }
}

data class NewsletterGmailerState(val placeholder: String) : ApplicationState
