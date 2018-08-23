package jobs.NewsletterGmailerJob

import config.FormattedJobName
import config.RequiredConfig
import config.RequiredConfigItem
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.BCC_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.BODY_A
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.BODY_B
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.DROPBOX_ACCESS_TOKEN
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.FOOTER
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.FROM_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.FROM_FULLNAME
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.GMAIL_ACCESS_TOKEN
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.GMAIL_CLIENT_SECRET
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.GMAIL_REFRESH_TOKEN
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.RUN_AFTER_TIME
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.RUN_AFTER_TZDB
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.RUN_ON_DAYS
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.SUBJECT_A
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.SUBJECT_B

sealed class NewsletterGmailerConfigItem(override val name: String) : RequiredConfigItem {
    class GMAIL_CLIENT_SECRET(jobName: FormattedJobName) : NewsletterGmailerConfigItem("${jobName.value}_GMAIL_CLIENT_SECRET")
    class GMAIL_ACCESS_TOKEN(jobName: FormattedJobName) : NewsletterGmailerConfigItem("${jobName.value}_GMAIL_ACCESS_TOKEN")
    class GMAIL_REFRESH_TOKEN(jobName: FormattedJobName) : NewsletterGmailerConfigItem("${jobName.value}_GMAIL_REFRESH_TOKEN")
    class DROPBOX_ACCESS_TOKEN(jobName: FormattedJobName) : NewsletterGmailerConfigItem("${jobName.value}_DROPBOX_ACCESS_TOKEN")
    class RUN_ON_DAYS(jobName: FormattedJobName) : NewsletterGmailerConfigItem("${jobName.value}_RUN_ON_DAYS")
    class RUN_AFTER_TIME(jobName: FormattedJobName) : NewsletterGmailerConfigItem("${jobName.value}_RUN_AFTER_TIME")
    class RUN_AFTER_TZDB(jobName: FormattedJobName) : NewsletterGmailerConfigItem("${jobName.value}_RUN_AFTER_TZDB")
    class FROM_ADDRESS(jobName: FormattedJobName) : NewsletterGmailerConfigItem(  "${jobName.value}_FROM_ADDRESS")
    class FROM_FULLNAME(jobName: FormattedJobName) : NewsletterGmailerConfigItem("${jobName.value}_FROM_FULLNAME")
    class BCC_ADDRESS(jobName: FormattedJobName) : NewsletterGmailerConfigItem(  "${jobName.value}_BCC_ADDRESS")
    class SUBJECT_A(jobName: FormattedJobName) : NewsletterGmailerConfigItem(  "${jobName.value}_SUBJECT_A")
    class SUBJECT_B(jobName: FormattedJobName) : NewsletterGmailerConfigItem("${jobName.value}_SUBJECT_B")
    class BODY_A(jobName: FormattedJobName) : NewsletterGmailerConfigItem(   "${jobName.value}_BODY_A")
    class BODY_B(jobName: FormattedJobName) : NewsletterGmailerConfigItem("${jobName.value}_BODY_B")
    class FOOTER(jobName: FormattedJobName) : NewsletterGmailerConfigItem("${jobName.value}_FOOTER")
}

class NewsletterGmailerConfig(jobName: String): RequiredConfig(jobName) {

    override fun values() = setOf(
        GMAIL_CLIENT_SECRET(formattedJobName),
        GMAIL_ACCESS_TOKEN(formattedJobName),
        GMAIL_REFRESH_TOKEN(formattedJobName),
        DROPBOX_ACCESS_TOKEN(formattedJobName),
        RUN_ON_DAYS(formattedJobName),
        RUN_AFTER_TIME(formattedJobName),
        RUN_AFTER_TZDB(formattedJobName),
        FROM_ADDRESS(formattedJobName),
        FROM_FULLNAME(formattedJobName),
        BCC_ADDRESS(formattedJobName),
        SUBJECT_A(formattedJobName),
        SUBJECT_B(formattedJobName),
        BODY_A(formattedJobName),
        BODY_B(formattedJobName),
        FOOTER(formattedJobName)
    )
}
