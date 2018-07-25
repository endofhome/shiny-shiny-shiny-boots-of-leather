package jobs.NewsletterGmailerJob

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.Configuration
import config.RequiredConfigItem
import jobs.GmailForwarderTest.FileLike
import jobs.GmailForwarderTest.StubDropboxClient
import jobs.GmailForwarderTest.StubGmailClient
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfig
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_BCC_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_FROM_FULLNAME
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_RUN_ON_DAYS
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_TO_ADDRESS
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class NewsletterGmailerTest {

    private val time = ZonedDateTime.of(2018, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    private val baseConfigValues = NewsletterGmailerConfig().values().associate { it to "unused" }.toMutableMap()
    private val configValues: Map<NewsletterGmailerConfigItem, String> = baseConfigValues.apply {
        set(NEWSLETTER_GMAILER_RUN_ON_DAYS, "1")
        set(NEWSLETTER_GMAILER_TO_ADDRESS, "jim@example.com")
        set(NEWSLETTER_GMAILER_FROM_FULLNAME, "bob@example.com")
        set(NEWSLETTER_GMAILER_BCC_ADDRESS, "fred@example.com")
    }.toMap()
    @Suppress("UNCHECKED_CAST")
    private val config = Configuration(configValues as Map<RequiredConfigItem, String>, NewsletterGmailerConfig(), null)

    @Test
    fun `Happy path when cleaning this week`() {
        val state =
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
        val stateFile = FileLike("/newsletter_gmailer.json", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val jobResult = NewsletterGmailer(StubGmailClient(emptyList()), dropboxClient, config).run(time)
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

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val jobResult = NewsletterGmailer(StubGmailClient(emptyList()), dropboxClient, config).run(time)
        assertThat(jobResult, equalTo(
                "There is no cleaning this week - an email reminder has been sent to Carla who is cleaning next week.\n" +
                        "Current state has been stored in Dropbox")
        )
    }
}
