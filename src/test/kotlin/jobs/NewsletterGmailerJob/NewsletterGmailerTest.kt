package jobs.NewsletterGmailerJob

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.Configuration
import config.RequiredConfigItem
import gmail.Email
import gmail.assertEmailEqual
import jobs.GmailForwarderTest.FileLike
import jobs.GmailForwarderTest.StubDropboxClient
import jobs.GmailForwarderTest.StubGmailClient
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfig
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_BODY_A
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_BODY_B
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_BCC_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_FROM_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_FROM_FULLNAME
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_RUN_ON_DAYS
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_SUBJECT_A
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_SUBJECT_B
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import javax.mail.internet.InternetAddress

class NewsletterGmailerTest {

    private val time = ZonedDateTime.of(2018, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    private val baseConfigValues = NewsletterGmailerConfig().values().associate { it to "unused" }.toMutableMap()
    private val configValues: Map<NewsletterGmailerConfigItem, String> = baseConfigValues.apply {
        set(NEWSLETTER_GMAILER_RUN_ON_DAYS, "1")
        set(NEWSLETTER_GMAILER_FROM_ADDRESS, "bob@example.com")
        set(NEWSLETTER_GMAILER_FROM_FULLNAME, "Bobby")
        set(NEWSLETTER_GMAILER_BCC_ADDRESS, "fred@example.com")
        set(NEWSLETTER_SUBJECT_A, "subject A")
        set(NEWSLETTER_BODY_A, "body A")
        set(NEWSLETTER_SUBJECT_B, "subject B")
        set(NEWSLETTER_BODY_B, "body B")
    }.toMap()
    @Suppress("UNCHECKED_CAST")
    private val config = Configuration(configValues as Map<RequiredConfigItem, String>, NewsletterGmailerConfig(), null)
    private val membersState =
            """
          |{
          |  "members": [
          |    {
          |      "name": "Milford",
          |      "email": "milford@graves.com"
          |    }
          |  ]
          |}
          |""".trimMargin()
    private val membersFile = FileLike("/members.json", membersState)

    @Test
    fun `Happy path when cleaning this week`() {
        val appState =
                """
          |{
          |  "status": "CLEANING_THIS_WEEK",
          |  "cleaner": {
          |    "name": "Milford",
          |    "email": "milford@graves.com"
          |  },
          |  "nextUp": {
          |    "name": "Carla",
          |    "surname": "Azar",
          |    "email": "carla@azar.com"
          |  },
          |  "lastRanOn": "2018-07-20",
          |  "emailContents": "some announcement contents"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/newsletter_gmailer.json", appState)

        val dropboxClient = StubDropboxClient(listOf(stateFile, membersFile))
        val gmailClient = StubGmailClient(emptyList())

        val expectedEmail = Email(
                from = InternetAddress(
                           config.get(NEWSLETTER_GMAILER_FROM_ADDRESS),
                           config.get(NEWSLETTER_GMAILER_FROM_FULLNAME)
                       ),
                to = listOf(InternetAddress("milford@graves.com", "Milford")),
                bcc = listOf(InternetAddress(config.get(NEWSLETTER_GMAILER_BCC_ADDRESS))),
                subject = config.get(NEWSLETTER_SUBJECT_A),
                body = config.get(NEWSLETTER_BODY_A)
        ).toGmailMessage()
        val jobResult = NewsletterGmailer(gmailClient, dropboxClient, config).run(time)

        assertEmailEqual(gmailClient.sentMail.last(), expectedEmail)
        assertThat(jobResult, equalTo(
                "Milford is cleaning this week - an email has been sent to all members.\n" +
                        "Current state has been stored in Dropbox")
        )
    }

    @Test
    fun `Happy path when no cleaning shift scheduled this week`() {
        val state =
                """
          |{
          |  "status": "NOT_CLEANING_THIS_WEEK",
          |  "nextUp": {
          |    "name": "Carla",
          |    "surname": "Azar",
          |    "email": "carla@azar.com"
          |  },
          |  "lastRanOn": "2018-07-27",
          |  "emailContents": "some reminder contents"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/newsletter_gmailer.json", state)

        val expectedEmail = Email(
                from = InternetAddress(
                        config.get(NEWSLETTER_GMAILER_FROM_ADDRESS),
                        config.get(NEWSLETTER_GMAILER_FROM_FULLNAME)
                ),
                to = listOf(InternetAddress("milford@graves.com", "Milford")),
                bcc = listOf(InternetAddress(config.get(NEWSLETTER_GMAILER_BCC_ADDRESS))),
                subject = config.get(NEWSLETTER_SUBJECT_B),
                body = config.get(NEWSLETTER_BODY_B)
        ).toGmailMessage()

        val dropboxClient = StubDropboxClient(listOf(stateFile, membersFile))
        val gmailClient = StubGmailClient(emptyList())
        val jobResult = NewsletterGmailer(gmailClient, dropboxClient, config).run(time)

        assertEmailEqual(gmailClient.sentMail.last(), expectedEmail)
        assertThat(jobResult, equalTo(
                "There is no cleaning this week - an email reminder has been sent to Carla Azar who is cleaning next week.\n" +
                        "Current state has been stored in Dropbox")
        )
    }
}
