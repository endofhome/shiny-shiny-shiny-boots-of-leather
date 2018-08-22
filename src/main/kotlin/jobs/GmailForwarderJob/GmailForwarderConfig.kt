package jobs.GmailForwarderJob

import config.FormattedJobName
import config.RequiredConfig
import config.RequiredConfigItem
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_FORWARDER_BCC_ADDRESS
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_FORWARDER_DROPBOX_ACCESS_TOKEN
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_FORWARDER_FROM_ADDRESS
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_FORWARDER_FROM_FULLNAME
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_FORWARDER_GMAIL_ACCESS_TOKEN
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_FORWARDER_GMAIL_CLIENT_SECRET
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_FORWARDER_GMAIL_QUERY
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_FORWARDER_GMAIL_REFRESH_TOKEN
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_FORWARDER_RUN_ON_DAYS
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_FORWARDER_TO_ADDRESS
import jobs.GmailForwarderJob.GmailForwarderConfigItem.GMAIL_FORWARDER_TO_FULLNAME


sealed class GmailForwarderConfigItem(override val name: String) : RequiredConfigItem {
    class GMAIL_FORWARDER_GMAIL_CLIENT_SECRET(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_GMAIL_CLIENT_SECRET")
    class GMAIL_FORWARDER_GMAIL_ACCESS_TOKEN(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_GMAIL_ACCESS_TOKEN")
    class GMAIL_FORWARDER_GMAIL_REFRESH_TOKEN(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_GMAIL_REFRESH_TOKEN")
    class GMAIL_FORWARDER_DROPBOX_ACCESS_TOKEN(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_DROPBOX_ACCESS_TOKEN")
    class GMAIL_FORWARDER_GMAIL_QUERY(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_GMAIL_QUERY")
    class GMAIL_FORWARDER_RUN_ON_DAYS(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_RUN_ON_DAYS")
    class GMAIL_FORWARDER_FROM_ADDRESS(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_FROM_ADDRESS")
    class GMAIL_FORWARDER_FROM_FULLNAME(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_FROM_FULLNAME")
    class GMAIL_FORWARDER_TO_ADDRESS(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_TO_ADDRESS")
    class GMAIL_FORWARDER_TO_FULLNAME(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_TO_FULLNAME")
    class GMAIL_FORWARDER_BCC_ADDRESS(jobName: FormattedJobName) : GmailForwarderConfigItem("${jobName.value}_BCC_ADDRESS")
}

class GmailForwarderConfig(jobName: String) : RequiredConfig(jobName) {

    override fun values() = setOf(
            GMAIL_FORWARDER_GMAIL_CLIENT_SECRET(formattedJobName),
            GMAIL_FORWARDER_GMAIL_ACCESS_TOKEN(jobName = formattedJobName),
            GMAIL_FORWARDER_GMAIL_REFRESH_TOKEN(formattedJobName),
            GMAIL_FORWARDER_DROPBOX_ACCESS_TOKEN(formattedJobName),
            GMAIL_FORWARDER_GMAIL_QUERY(formattedJobName),
            GMAIL_FORWARDER_RUN_ON_DAYS(formattedJobName),
            GMAIL_FORWARDER_FROM_ADDRESS(formattedJobName),
            GMAIL_FORWARDER_FROM_FULLNAME(formattedJobName),
            GMAIL_FORWARDER_TO_ADDRESS(formattedJobName),
            GMAIL_FORWARDER_TO_FULLNAME(formattedJobName),
            GMAIL_FORWARDER_BCC_ADDRESS(formattedJobName)
    )
}
