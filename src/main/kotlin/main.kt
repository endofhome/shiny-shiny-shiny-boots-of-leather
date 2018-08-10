import jobs.GmailForwarderJob.GmailForwarder
import jobs.Job
import jobs.NewsletterGmailerJob.NewsletterGmailer
import java.time.ZonedDateTime

fun main(args: Array<String>) {
    val gmailForwarder = GmailForwarder.initialise()
    val newsletterGmailer = NewsletterGmailer.initialise()
    val jobs: List<Job> = listOf(gmailForwarder, newsletterGmailer)

    jobs.forEach { job ->
        val result = job.run(ZonedDateTime.now())
        println("${job.jobName}: $result")
    }
}
