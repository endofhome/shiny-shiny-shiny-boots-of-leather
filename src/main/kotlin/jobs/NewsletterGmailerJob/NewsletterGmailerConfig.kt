package jobs.NewsletterGmailerJob

import config.RequiredConfig
import config.RequiredConfigItem
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_BCC_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_BODY_A
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_BODY_B
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_DROPBOX_ACCESS_TOKEN
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_FOOTER
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_FROM_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_FROM_FULLNAME
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_GMAIL_ACCESS_TOKEN
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_GMAIL_CLIENT_SECRET
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_GMAIL_REFRESH_TOKEN
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_RUN_AFTER_TIME
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_RUN_AFTER_TZDB
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_RUN_ON_DAYS
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_SUBJECT_A
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_SUBJECT_B

sealed class NewsletterGmailerConfigItem(override val name: String) : RequiredConfigItem {
    class NEWSLETTER_GMAILER_GMAIL_CLIENT_SECRET(jobName: String) : NewsletterGmailerConfigItem("${jobName}_GMAIL_CLIENT_SECRET")
    class NEWSLETTER_GMAILER_GMAIL_ACCESS_TOKEN(jobName: String) : NewsletterGmailerConfigItem("${jobName}_GMAIL_ACCESS_TOKEN")
    class NEWSLETTER_GMAILER_GMAIL_REFRESH_TOKEN(jobName: String) : NewsletterGmailerConfigItem("${jobName}_GMAIL_REFRESH_TOKEN")
    class NEWSLETTER_GMAILER_DROPBOX_ACCESS_TOKEN(jobName: String) : NewsletterGmailerConfigItem("${jobName}_DROPBOX_ACCESS_TOKEN")
    class NEWSLETTER_GMAILER_RUN_ON_DAYS(jobName: String) : NewsletterGmailerConfigItem("${jobName}_RUN_ON_DAYS")
    class NEWSLETTER_GMAILER_RUN_AFTER_TIME(jobName: String) : NewsletterGmailerConfigItem("${jobName}_RUN_AFTER_TIME")
    class NEWSLETTER_GMAILER_FROM_ADDRESS(jobName: String) : NewsletterGmailerConfigItem(  "${jobName}_FROM_ADDRESS")
    class NEWSLETTER_GMAILER_FROM_FULLNAME(jobName: String) : NewsletterGmailerConfigItem("${jobName}_FROM_FULLNAME")
    class NEWSLETTER_GMAILER_BCC_ADDRESS(jobName: String) : NewsletterGmailerConfigItem(  "${jobName}_BCC_ADDRESS")
    class NEWSLETTER_GMAILER_SUBJECT_A(jobName: String) : NewsletterGmailerConfigItem(  "${jobName}_SUBJECT_A")
    class NEWSLETTER_GMAILER_SUBJECT_B(jobName: String) : NewsletterGmailerConfigItem("${jobName}_SUBJECT_B")
    class NEWSLETTER_GMAILER_BODY_A(jobName: String) : NewsletterGmailerConfigItem(   "${jobName}_BODY_A")
    class NEWSLETTER_GMAILER_BODY_B(jobName: String) : NewsletterGmailerConfigItem("${jobName}_BODY_B")
    class NEWSLETTER_GMAILER_FOOTER(jobName: String) : NewsletterGmailerConfigItem("${jobName}_FOOTER")
    class NEWSLETTER_GMAILER_RUN_AFTER_TZDB(jobName: String) : NewsletterGmailerConfigItem("${jobName}_RUN_AFTER_TZDB")
}

class NewsletterGmailerConfig(override val jobName: String): RequiredConfig {
    override val formattedJobName: String = formatJobName()

    override fun values() = setOf(
            NEWSLETTER_GMAILER_GMAIL_CLIENT_SECRET(formattedJobName),
            NEWSLETTER_GMAILER_GMAIL_ACCESS_TOKEN(formattedJobName),
            NEWSLETTER_GMAILER_GMAIL_REFRESH_TOKEN(formattedJobName),
            NEWSLETTER_GMAILER_DROPBOX_ACCESS_TOKEN(formattedJobName),
            NEWSLETTER_GMAILER_RUN_ON_DAYS(formattedJobName),
            NEWSLETTER_GMAILER_RUN_AFTER_TIME(formattedJobName),
            NEWSLETTER_GMAILER_FROM_ADDRESS(formattedJobName),
            NEWSLETTER_GMAILER_FROM_FULLNAME(formattedJobName),
            NEWSLETTER_GMAILER_BCC_ADDRESS(formattedJobName),
            NEWSLETTER_GMAILER_SUBJECT_A(formattedJobName),
            NEWSLETTER_GMAILER_SUBJECT_B(formattedJobName),
            NEWSLETTER_GMAILER_BODY_A(formattedJobName),
            NEWSLETTER_GMAILER_BODY_B(formattedJobName),
            NEWSLETTER_GMAILER_FOOTER(formattedJobName),
            NEWSLETTER_GMAILER_RUN_AFTER_TZDB(formattedJobName)
    )
}