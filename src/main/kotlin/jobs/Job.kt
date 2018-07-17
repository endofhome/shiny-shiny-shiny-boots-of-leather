package jobs

import java.time.ZonedDateTime

interface Job {
    fun run(now: ZonedDateTime, daysOfMonthToRun: List<Int>): String
    fun daysOfMonthToRun(): List<Int>
}

interface JobCompanion {
    fun initialise(): Job
}