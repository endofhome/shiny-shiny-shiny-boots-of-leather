package jobs.GmailForwarderJob

import config.FormattedJobName
import config.RequiredConfig
import config.RequiredConfigItem
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


sealed class GmailForwarderConfigItem(override val name: String) : RequiredConfigItem {
    class GMAIL_CLIENT_SECRET(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_GMAIL_CLIENT_SECRET")
    class GMAIL_ACCESS_TOKEN(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_GMAIL_ACCESS_TOKEN")
    class GMAIL_REFRESH_TOKEN(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_GMAIL_REFRESH_TOKEN")
    class DROPBOX_ACCESS_TOKEN(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_DROPBOX_ACCESS_TOKEN")
    class GMAIL_QUERY(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_GMAIL_QUERY")
    class RUN_ON_DAYS(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_RUN_ON_DAYS")
    class FROM_ADDRESS(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_FROM_ADDRESS")
    class FROM_FULLNAME(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_FROM_FULLNAME")
    class TO_ADDRESS(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_TO_ADDRESS")
    class TO_FULLNAME(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_TO_FULLNAME")
    class BCC_ADDRESS(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_BCC_ADDRESS")
}

class GmailForwarderConfig(jobName: String) : RequiredConfig(jobName) {

    override fun values() = setOf(
            GMAIL_CLIENT_SECRET(formattedJobName),
            GMAIL_ACCESS_TOKEN(jobName = formattedJobName),
            GMAIL_REFRESH_TOKEN(formattedJobName),
            DROPBOX_ACCESS_TOKEN(formattedJobName),
            GMAIL_QUERY(formattedJobName),
            RUN_ON_DAYS(formattedJobName),
            FROM_ADDRESS(formattedJobName),
            FROM_FULLNAME(formattedJobName),
            TO_ADDRESS(formattedJobName),
            TO_FULLNAME(formattedJobName),
            BCC_ADDRESS(formattedJobName)
    )
}
