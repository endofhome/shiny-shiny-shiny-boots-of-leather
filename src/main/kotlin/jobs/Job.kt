package jobs

import config.FormattedJobName
import config.RequiredConfig
import java.time.ZonedDateTime

interface Job {
    val jobName: FormattedJobName
    fun run(now: ZonedDateTime): String
}

interface JobCompanion {
    fun initialise(requiredConfig: RequiredConfig): Job
}
