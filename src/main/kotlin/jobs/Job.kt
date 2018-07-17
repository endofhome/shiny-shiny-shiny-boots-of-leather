package jobs

import java.time.ZonedDateTime

interface Job {
    val jobName: String
    fun run(now: ZonedDateTime, daysOfMonthToRun: List<Int>): String
    fun daysOfMonthToRun(): List<Int>
}

interface JobCompanion {
    fun initialise(): Job
}