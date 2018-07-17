import jobs.GmailForwarder
import jobs.Job
import java.time.ZonedDateTime

fun main(args: Array<String>) {
    val gmailForwarder = GmailForwarder.initialise()
    val jobs: List<Job> = listOf(gmailForwarder)

    jobs.forEach { job ->
        val result = job.run(ZonedDateTime.now())
        println("${job.jobName}: $result")
    }
}
