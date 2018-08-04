package jobs.NewsletterGmailerJob

import com.google.api.services.gmail.model.Message
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.Configuration
import config.RequiredConfigItem
import datastore.DropboxDatastore
import datastore.ErrorDownloadingFileFromDropbox
import datastore.FlatFileApplicationStateMetadata
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
import result.Result
import result.Result.Failure
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
    private val appStatefilename = "/newsletter_gmailer.json"
    private val appStateMetadata = FlatFileApplicationStateMetadata(appStatefilename, NewsletterGmailerState::class.java)
    private val membersMetadata = FlatFileApplicationStateMetadata(membersFile.name, NewsletterGmailer.Members::class.java)


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
        val stateFile = FileLike(appStatefilename, appState)

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
        val jobResult = NewsletterGmailer(gmailClient, DropboxDatastore(dropboxClient, appStateMetadata), DropboxDatastore(dropboxClient, membersMetadata), config).run(time)

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
        val stateFile = FileLike(appStatefilename, state)

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
        val jobResult = NewsletterGmailer(gmailClient, DropboxDatastore(dropboxClient, appStateMetadata), DropboxDatastore(dropboxClient, membersMetadata), config).run(time)

        assertEmailEqual(gmailClient.sentMail.last(), expectedEmail)
        assertThat(jobResult, equalTo(
                "There is no cleaning this week - an email reminder has been sent to Carla Azar who is cleaning next week.\n" +
                        "Current state has been stored in Dropbox")
        )
    }

    @Test
    fun `Error message is provided when current application state cannot be retrieved`() {
        val stateFile = FileLike("don't care", "don't care")
        val dropboxClientThatCannotRead = StubDropboxClientThatCannotRead()
        val dropboxClient = StubDropboxClient(listOf(stateFile, membersFile))
        val gmailClient = StubGmailClient(emptyList())

        val failingAppStateDatastore = DropboxDatastore(dropboxClientThatCannotRead, appStateMetadata)
        val successfulMembersDatastore = DropboxDatastore(dropboxClient, membersMetadata)
        val jobResult = NewsletterGmailer(gmailClient, failingAppStateDatastore, successfulMembersDatastore, config).run(time)

        assertThat(gmailClient.sentMail, equalTo(emptyList<Message>()))
        assertThat(jobResult, equalTo("Error downloading file /newsletter_gmailer.json from Dropbox"))
    }

    @Test
    fun `Error message is provided when current members list cannot be retrieved`() {
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
        val stateFile = FileLike(appStatefilename, appState)
        val dropboxClientThatCannotRead = StubDropboxClientThatCannotRead()
        val dropboxClient = StubDropboxClient(listOf(stateFile, membersFile))
        val gmailClient = StubGmailClient(emptyList())

        val successfulAppStateDatastore = DropboxDatastore(dropboxClient, appStateMetadata)
        val failingMembersDatastore = DropboxDatastore(dropboxClientThatCannotRead, membersMetadata)
        val jobResult = NewsletterGmailer(gmailClient, successfulAppStateDatastore, failingMembersDatastore, config).run(time)

        assertThat(gmailClient.sentMail, equalTo(emptyList<Message>()))
        assertThat(jobResult, equalTo("Error downloading file /members.json from Dropbox"))
    }
}

class StubDropboxClientThatCannotRead(initialFiles: List<FileLike> = emptyList()) : StubDropboxClient(initialFiles) {
    override fun readFile(filename: String): Result<ErrorDownloadingFileFromDropbox, String> =
            Failure(ErrorDownloadingFileFromDropbox(filename))
}
