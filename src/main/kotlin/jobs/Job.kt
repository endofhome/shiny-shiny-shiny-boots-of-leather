package jobs

import config.RequiredConfig
import java.time.ZonedDateTime

interface Job {
    val jobName: String
    fun run(now: ZonedDateTime): String
}

interface JobCompanion {
    fun initialise(requiredConfig: RequiredConfig): Job
}
