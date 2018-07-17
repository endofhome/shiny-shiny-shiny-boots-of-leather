import jobs.GmailBot
import jobs.Job
import java.time.ZonedDateTime

fun main(args: Array<String>) {
    val gmailJob = GmailBot.initialise()
    val jobs: List<Job> = listOf(gmailJob)

    jobs.forEach { job ->
        val result = job.run(ZonedDateTime.now(), job.daysOfMonthToRun())
        println(result)
    }
}
