package jobs.CleaningRotaGmailerJob

import config.FormattedJobName
import config.RequiredConfig
import config.RequiredConfigItem
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.BCC_ADDRESS
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.BODY_A
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.BODY_B
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.DROPBOX_ACCESS_TOKEN
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.FOOTER
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.FROM_ADDRESS
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.FROM_FULLNAME
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.GMAIL_ACCESS_TOKEN
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.GMAIL_CLIENT_SECRET
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.GMAIL_REFRESH_TOKEN
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.RUN_AFTER_TIME
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.RUN_AFTER_TZDB
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.RUN_ON_DAYS
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.SUBJECT_A
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.SUBJECT_B

sealed class CleaningRotaGmailerConfigItem(override val name: String) : RequiredConfigItem {
    class GMAIL_CLIENT_SECRET(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem("${jobName.value}_GMAIL_CLIENT_SECRET")
    class GMAIL_ACCESS_TOKEN(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem("${jobName.value}_GMAIL_ACCESS_TOKEN")
    class GMAIL_REFRESH_TOKEN(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem("${jobName.value}_GMAIL_REFRESH_TOKEN")
    class DROPBOX_ACCESS_TOKEN(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem("${jobName.value}_DROPBOX_ACCESS_TOKEN")
    class RUN_ON_DAYS(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem("${jobName.value}_RUN_ON_DAYS")
    class RUN_AFTER_TIME(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem("${jobName.value}_RUN_AFTER_TIME")
    class RUN_AFTER_TZDB(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem("${jobName.value}_RUN_AFTER_TZDB")
    class FROM_ADDRESS(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem(  "${jobName.value}_FROM_ADDRESS")
    class FROM_FULLNAME(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem("${jobName.value}_FROM_FULLNAME")
    class BCC_ADDRESS(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem(  "${jobName.value}_BCC_ADDRESS")
    class SUBJECT_A(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem(  "${jobName.value}_SUBJECT_A")
    class SUBJECT_B(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem("${jobName.value}_SUBJECT_B")
    class BODY_A(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem(   "${jobName.value}_BODY_A")
    class BODY_B(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem("${jobName.value}_BODY_B")
    class FOOTER(jobName: FormattedJobName) : CleaningRotaGmailerConfigItem("${jobName.value}_FOOTER")
}

class CleaningRotaGmailerConfig(jobName: String): RequiredConfig(jobName) {

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
