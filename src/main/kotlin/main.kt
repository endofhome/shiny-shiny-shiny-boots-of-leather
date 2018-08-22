import jobs.GmailForwarderJob.GmailForwarder
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig
import jobs.Job
import jobs.NewsletterGmailerJob.NewsletterGmailer
import jobs.NewsletterGmailerJob.NewsletterGmailerConfig
import java.time.ZonedDateTime

fun main(args: Array<String>) {
    val gmailForwarder = GmailForwarder.initialise("unused job name", GmailForwarderConfig())
    val newsletterGmailer = NewsletterGmailer.initialise("some job", NewsletterGmailerConfig("some job"))
    val jobs: List<Job> = listOf(
            gmailForwarder,
            newsletterGmailer
    )

    jobs.forEach { job ->
        val result = job.run(ZonedDateTime.now())
        println("${job.jobName}: $result")
    }
}
