package jobs.NewsletterGmailerJob

import com.google.api.services.gmail.model.Message
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.Configuration
import config.RequiredConfigItem
import datastore.DropboxDatastore
import datastore.ErrorDownloadingFileFromDropbox
import datastore.FlatFileApplicationStateMetadata
import datastore.expectSuccess
import gmail.Email
import gmail.assertEmailEqual
import jobs.GmailForwarderTest.FileLike
import jobs.GmailForwarderTest.StubDropboxClient
import jobs.GmailForwarderTest.StubGmailClient
import jobs.GmailForwarderTest.StubGmailClientThatCannotSend
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfig
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_BCC_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_BODY_A
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_BODY_B
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_FOOTER
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_FROM_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_FROM_FULLNAME
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_RUN_AFTER_TIME
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_RUN_ON_DAYS
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_SUBJECT_A
import jobs.NewsletterGmailerJob.NewsletterGmailer.Companion.NewsletterGmailerConfigItem.NEWSLETTER_GMAILER_SUBJECT_B
import jobs.NewsletterGmailerJob.TemplatedMessage.CompiledTemplate
import jobs.NewsletterGmailerJob.TemplatedMessage.RawTemplate
import org.junit.Test
import result.Result
import result.Result.Failure
import java.time.ZoneOffset
import java.time.ZonedDateTime
import javax.mail.internet.InternetAddress

class NewsletterGmailerTest {

    private val time = ZonedDateTime.of(2018, 6, 4, 10, 30, 0, 0, ZoneOffset.UTC)
    private val baseConfigValues = NewsletterGmailerConfig().values().associate { it to "unused" }.toMutableMap()
    private val configValues: Map<NewsletterGmailerConfigItem, String> = baseConfigValues.apply {
        set(NEWSLETTER_GMAILER_RUN_ON_DAYS, "Monday")
        set(NEWSLETTER_GMAILER_RUN_AFTER_TIME, "10:30")
        set(NEWSLETTER_GMAILER_FROM_ADDRESS, "bob@example.com")
        set(NEWSLETTER_GMAILER_FROM_FULLNAME, "Bobby")
        set(NEWSLETTER_GMAILER_BCC_ADDRESS, "fred@example.com")
        set(NEWSLETTER_GMAILER_SUBJECT_A, "subject A with {{cleaner}}")
        set(NEWSLETTER_GMAILER_BODY_A, "body A with {{cleaner}}")
        set(NEWSLETTER_GMAILER_SUBJECT_B, "subject B with {{cleaner}}")
        set(NEWSLETTER_GMAILER_BODY_B, "body B with {{cleaner}}")
        set(NEWSLETTER_GMAILER_FOOTER, "<br>some footer")
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
          |    },
          |    {
          |      "name": "Carla",
          |      "surname": "Azar",
          |      "email": "carla@azar.com"
          |    }
          |  ]
          |}
          |""".trimMargin()
    private val appStatefilename = "/newsletter_gmailer.json"
    private val membersFilename = "/members.json"
    private val membersFile = FileLike(membersFilename, membersState)
    private val appStateMetadata = FlatFileApplicationStateMetadata(appStatefilename, NewsletterGmailerState::class.java)
    private val membersMetadata = FlatFileApplicationStateMetadata(membersFile.name, NewsletterGmailer.Members::class.java)

    @Test
    fun `Happy path when no cleaning shift scheduled this week`() {
        val nextUpName = "Milford"
        val nextUpEmailAddress = "milford@graves.com"
        val appState =
          """
          |{
          |  "status": "CLEANING_THIS_WEEK",
          |  "cleaner": {
          |    "name": "Carla",
          |    "surname": "Azar",
          |    "email": "carla@azar.com"
          |  },
          |  "nextUp": {
          |    "name": "$nextUpName",
          |    "email": "$nextUpEmailAddress"
          |  },
          |  "lastRanOn": "2018-07-20",
          |  "emailContents": "some announcement contents"
          |}
          |""".trimMargin()
        val stateFile = FileLike(appStatefilename, appState)

        val dropboxClient = StubDropboxClient(mapOf(appStatefilename to stateFile, membersFilename to membersFile))
        val gmailClient = StubGmailClient(emptyList())

        val emailModel = mapOf("cleaner" to nextUpName)
        val expectedEmail = Email(
            from = InternetAddress(
                config.get(NEWSLETTER_GMAILER_FROM_ADDRESS),
                config.get(NEWSLETTER_GMAILER_FROM_FULLNAME)
            ),
            to = listOf(InternetAddress(nextUpEmailAddress, nextUpName)),
            bcc = listOf(InternetAddress(config.get(NEWSLETTER_GMAILER_BCC_ADDRESS))),
            subject = CompiledTemplate.from(RawTemplate(config.get(NEWSLETTER_GMAILER_SUBJECT_B)), emailModel).value,
            body = CompiledTemplate.from(RawTemplate(config.get(NEWSLETTER_GMAILER_BODY_B) + config.get(NEWSLETTER_GMAILER_FOOTER)), emailModel).value
        ).toGmailMessage()

        val expectedEndState =
          """
          |{
          |  "status": "NOT_CLEANING_THIS_WEEK",
          |  "nextUp": {
          |    "name": "Carla",
          |    "surname": "Azar",
          |    "email": "carla@azar.com"
          |  },
          |  "lastRanOn": "2018-06-04",
          |  "emailContents": "body B with Milford<br>some footer"
          |}
          |""".trimMargin()

        val appStateDatastore = DropboxDatastore(dropboxClient, appStateMetadata)
        val jobResult = NewsletterGmailer(gmailClient, appStateDatastore, DropboxDatastore(dropboxClient, membersMetadata), config).run(time)

        assertEmailEqual(gmailClient.sentMail.last(), expectedEmail)
        assertThat(appStateDatastore.currentApplicationState().expectSuccess().asJsonString(), equalTo(expectedEndState.normaliseJsonString()))
        assertThat(jobResult, equalTo(
            "There is no cleaning this week - an email reminder has been sent to Milford who is cleaning next week.\n" +
                "Current state has been stored in Dropbox")
        )
    }

    @Test
    fun `Happy path when cleaning this week`() {
        val nextUpFirstName = "Carla"
        val nextUpLastName = "Azar"
        val nextUpFullName = "$nextUpFirstName $nextUpLastName"
        val state =
          """
          |{
          |  "status": "NOT_CLEANING_THIS_WEEK",
          |  "nextUp": {
          |    "name": "$nextUpFirstName",
          |    "surname": "$nextUpLastName",
          |    "email": "carla@azar.com"
          |  },
          |  "lastRanOn": "2018-07-27",
          |  "emailContents": "some reminder contents"
          |}
          |""".trimMargin()
        val stateFile = FileLike(appStatefilename, state)

        val emailModel = mapOf("cleaner" to nextUpFullName)
        val expectedEmail = Email(
            from = InternetAddress(
                config.get(NEWSLETTER_GMAILER_FROM_ADDRESS),
                config.get(NEWSLETTER_GMAILER_FROM_FULLNAME)
            ),
            to = listOf(InternetAddress("milford@graves.com", "Milford"), InternetAddress("carla@azar.com", "Carla Azar")),
            bcc = listOf(InternetAddress(config.get(NEWSLETTER_GMAILER_BCC_ADDRESS))),
            subject = CompiledTemplate.from(RawTemplate(config.get(NEWSLETTER_GMAILER_SUBJECT_A)), emailModel).value,
            body = CompiledTemplate.from(RawTemplate(config.get(NEWSLETTER_GMAILER_BODY_A) + config.get(NEWSLETTER_GMAILER_FOOTER)), emailModel).value
        ).toGmailMessage()

        val expectedEndState =
          """
          |{
          |  "status": "CLEANING_THIS_WEEK",
          |  "cleaner": {
          |    "name": "Carla",
          |    "surname": "Azar",
          |    "email": "carla@azar.com"
          |  },
          |  "nextUp": {
          |    "name": "Milford",
          |    "email": "milford@graves.com"
          |  },
          |  "lastRanOn": "2018-06-04",
          |  "emailContents": "body A with Carla Azar<br>some footer"
          |}
          |""".trimMargin()

        val dropboxClient = StubDropboxClient(mapOf(appStatefilename to stateFile, membersFilename to membersFile))
        val gmailClient = StubGmailClient(emptyList())
        val appStateDatastore = DropboxDatastore(dropboxClient, appStateMetadata)
        val jobResult = NewsletterGmailer(gmailClient, appStateDatastore, DropboxDatastore(dropboxClient, membersMetadata), config).run(time)

        assertEmailEqual(gmailClient.sentMail.last(), expectedEmail)
        assertThat(appStateDatastore.currentApplicationState().expectSuccess().asJsonString(), equalTo(expectedEndState.normaliseJsonString()))
        assertThat(jobResult, equalTo(
            "Carla Azar is cleaning this week - an email has been sent to all members.\n" +
                "Current state has been stored in Dropbox")
        )
    }

    @Test
    fun `Error message is provided when current application state cannot be retrieved`() {
        val stateFile = FileLike("don't care", "don't care")
        val dropboxClientThatCannotRead = StubDropboxClientThatCannotRead()
        val dropboxClient = StubDropboxClient(mapOf(appStatefilename to stateFile, membersFilename to membersFile))
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
        val dropboxClient = StubDropboxClient(mapOf(appStatefilename to stateFile, membersFilename to membersFile))
        val gmailClient = StubGmailClient(emptyList())

        val successfulAppStateDatastore = DropboxDatastore(dropboxClient, appStateMetadata)
        val failingMembersDatastore = DropboxDatastore(dropboxClientThatCannotRead, membersMetadata)
        val jobResult = NewsletterGmailer(gmailClient, successfulAppStateDatastore, failingMembersDatastore, config).run(time)

        assertThat(gmailClient.sentMail, equalTo(emptyList<Message>()))
        assertThat(jobResult, equalTo("Error downloading file /members.json from Dropbox"))
    }

    @Test
    fun `Error message when email cannot be sent`() {
        val appState =
          """
          |{
          |  "status": "NOT_CLEANING_THIS_WEEK",
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
        val dropboxClient = StubDropboxClient(mapOf(appStatefilename to stateFile, membersFilename to membersFile))
        val gmailClient = StubGmailClientThatCannotSend(emptyList())

        val appDatastore = DropboxDatastore(dropboxClient, appStateMetadata)
        val membersDatastore = DropboxDatastore(dropboxClient, membersMetadata)
        val jobResult = NewsletterGmailer(gmailClient, appDatastore, membersDatastore, config).run(time)

        assertThat(gmailClient.sentMail, equalTo(emptyList<Message>()))
        assertThat(jobResult, equalTo("Error sending email with subject 'subject A with Carla Azar' to Milford <milford@graves.com>, Carla Azar <carla@azar.com>"))
    }

    @Test
    fun `Email is only sent on a particular day of the week`() {
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
        val dropboxClient = StubDropboxClient(mapOf(appStatefilename to stateFile, membersFilename to membersFile))
        val gmailClient = StubGmailClient(emptyList())
        val firstOfJune = ZonedDateTime.of(2018, 6, 3, 0, 0, 0, 0, ZoneOffset.UTC)
        val localConfig = config.copy(
                config = configValues.toMutableMap()
                        .apply { set(NEWSLETTER_GMAILER_RUN_ON_DAYS, "Monday, Wednesday, Thursday") }
                        .toMap()
        )
        val jobResult = NewsletterGmailer(gmailClient, DropboxDatastore(dropboxClient, appStateMetadata), DropboxDatastore(dropboxClient, membersMetadata), localConfig).run(firstOfJune)

        assertThat(jobResult, equalTo("No need to run - today is Sunday, only running on Monday, Wednesday, Thursday"))
    }

    @Test
    fun `Email is only sent after a certain time of day`() {
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
        val dropboxClient = StubDropboxClient(mapOf(appStatefilename to stateFile, membersFilename to membersFile))
        val gmailClient = StubGmailClient(emptyList())
        val beforeTenThirty = ZonedDateTime.of(2018, 6, 4, 4, 14, 59, 0, ZoneOffset.UTC)
        val localConfig = config.copy(
                config = configValues.toMutableMap()
                        .apply { set(NEWSLETTER_GMAILER_RUN_AFTER_TIME, "04:15") }
                        .toMap()
        )
        val jobResult = NewsletterGmailer(gmailClient, DropboxDatastore(dropboxClient, appStateMetadata), DropboxDatastore(dropboxClient, membersMetadata), localConfig).run(beforeTenThirty)

        assertThat(jobResult, equalTo("No need to run - time is 4:14, only running after 4:15"))
    }

    @Test
    fun `Email isn't sent if the exact same email contents have already been sent`() {
        val appState =
          """
          |{
          |  "status": "NOT_CLEANING_THIS_WEEK",
          |  "nextUp": {
          |    "name": "Carla",
          |    "surname": "Azar",
          |    "email": "carla@azar.com"
          |  },
          |  "lastRanOn": "2018-07-20",
          |  "emailContents": "From: Bobby <bob@example.com>
          |                    To: Milford <milford@graves.com>, Carla Azar <carla@azar.com>
          |                    Bcc: fred@example.com
          |                    Subject: subject A with Carla Azar
          |                    MIME-Version: 1.0
          |                    Content-Type: text/html; charset=utf-8; format=flowed
          |                    Content-Transfer-Encoding: 7bit
          |
          |                    body A with Carla Azar<br>some footer"
          |}
          |""".trimMargin().trimIndent()
        val stateFile = FileLike(appStatefilename, appState)
        val dropboxClient = StubDropboxClient(mapOf(appStatefilename to stateFile, membersFilename to membersFile))

        val jobResult = NewsletterGmailer(StubGmailClient(emptyList()), DropboxDatastore(dropboxClient, appStateMetadata), DropboxDatastore(dropboxClient, membersMetadata), config).run(time)

        assertThat(jobResult, equalTo("Exiting as this exact email has already been sent"))
    }

    @Test
    fun `Email shouldn't be sent twice on the same day`() {
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
          |  "lastRanOn": "2018-06-04",
          |  "emailContents": "any old contents"
          |}
          |""".trimMargin().trimIndent()
        val stateFile = FileLike(appStatefilename, appState)
        val dropboxClient = StubDropboxClient(mapOf(appStatefilename to stateFile, membersFilename to membersFile))

        val jobResult = NewsletterGmailer(StubGmailClient(emptyList()), DropboxDatastore(dropboxClient, appStateMetadata), DropboxDatastore(dropboxClient, membersMetadata), config).run(time)

        assertThat(jobResult, equalTo("Exiting as an email has already been sent today"))
    }
}

private fun String.normaliseJsonString(): String =
    this.replace("\n", "")
        .replace(Regex("\\s+\""), "\"")
        .replace(Regex("\\s+}"), "}")
        .replace(Regex(":\\s+\\{"), ":{")

class StubDropboxClientThatCannotRead(initialFiles: Map<String, FileLike> = mapOf()) : StubDropboxClient(initialFiles) {
    override fun readFile(filename: String): Result<ErrorDownloadingFileFromDropbox, String> =
            Failure(ErrorDownloadingFileFromDropbox(filename))
}
