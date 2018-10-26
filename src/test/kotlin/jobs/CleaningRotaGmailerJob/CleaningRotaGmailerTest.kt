package jobs.CleaningRotaGmailerJob

import com.google.api.services.gmail.model.Message
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.Configuration
import config.FormattedJobName
import config.RequiredConfigItem
import config.removeAndSet
import datastore.DropboxDatastore
import datastore.ErrorDownloadingFileFromDropbox
import datastore.FlatFileApplicationStateMetadata
import datastore.expectSuccess
import gmail.Email
import gmail.assertEmailEqual
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.BCC_ADDRESS
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.BODY_A
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.BODY_B
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.FOOTER
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.FROM_ADDRESS
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.FROM_FULLNAME
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.RUN_AFTER_TIME
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.RUN_AFTER_TZDB
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.RUN_ON_DAYS
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.SUBJECT_A
import jobs.CleaningRotaGmailerJob.CleaningRotaGmailerConfigItem.SUBJECT_B
import jobs.CleaningRotaGmailerJob.TemplatedMessage.CompiledTemplate
import jobs.CleaningRotaGmailerJob.TemplatedMessage.RawTemplate
import jobs.GmailForwarderTest.FileLike
import jobs.GmailForwarderTest.StubDropboxClient
import jobs.GmailForwarderTest.StubGmailClient
import jobs.GmailForwarderTest.StubGmailClientThatCannotSend
import org.junit.Test
import result.Result
import result.Result.Failure
import java.time.ZoneOffset
import java.time.ZonedDateTime
import javax.mail.internet.InternetAddress

val jobName = FormattedJobName("TEST_JOB")

class CleaningRotaGmailerTest {

    private val time = ZonedDateTime.of(2018, 6, 4, 10, 30, 0, 0, ZoneOffset.UTC)
    private val baseConfigValues = CleaningRotaGmailerConfig(jobName.value).values().associate { it to "unused" }.toMutableMap()
    private val configValues: Map<CleaningRotaGmailerConfigItem, String> = baseConfigValues.apply {
        removeAndSet(RUN_ON_DAYS(jobName), "Monday")
        removeAndSet(RUN_AFTER_TIME(jobName), "10:30")
        removeAndSet(FROM_ADDRESS(jobName), "bob@example.com")
        removeAndSet(FROM_FULLNAME(jobName), "Bobby")
        removeAndSet(BCC_ADDRESS(jobName), "fred@example.com")
        removeAndSet(SUBJECT_A(jobName), "subject A with {{cleaner}}")
        removeAndSet(BODY_A(jobName), "body A with {{cleaner}}")
        removeAndSet(SUBJECT_B(jobName), "subject B with {{cleaner}}")
        removeAndSet(BODY_B(jobName), "body B with {{cleaner}}")
        removeAndSet(FOOTER(jobName), "<br>some footer")
        removeAndSet(RUN_AFTER_TZDB(jobName), "Europe/London")
    }.toMap()

    @Suppress("UNCHECKED_CAST")
    private val config = Configuration(configValues as Map<RequiredConfigItem, String>, CleaningRotaGmailerConfig(jobName.value), null)
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
    private val appStatefilename = "/cleaning_rota_gmailer.json"
    private val membersFilename = "/members.json"
    private val membersFile = FileLike(membersFilename, membersState)
    private val appStateMetadata = FlatFileApplicationStateMetadata(appStatefilename, CleaningRotaGmailerState::class.java)
    private val membersMetadata = FlatFileApplicationStateMetadata(membersFile.name, CleaningRotaGmailer.Members::class.java)

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
                config.get(FROM_ADDRESS(jobName)),
                config.get(FROM_FULLNAME(jobName))
            ),
            to = listOf(InternetAddress("milford@graves.com", "Milford"), InternetAddress("carla@azar.com", "Carla Azar")),
            bcc = listOf(InternetAddress(config.get(BCC_ADDRESS(jobName)))),
            subject = CompiledTemplate.from(RawTemplate(config.get(SUBJECT_A(jobName))), emailModel).value,
            body = CompiledTemplate.from(RawTemplate(config.get(BODY_A(jobName)) + config.get(FOOTER(jobName))), emailModel).value
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
        val jobResult = CleaningRotaGmailer(gmailClient, appStateDatastore, DropboxDatastore(dropboxClient, membersMetadata), config).run(time)

        assertEmailEqual(gmailClient.sentMail.last(), expectedEmail)
        assertThat(appStateDatastore.currentApplicationState().expectSuccess().asJsonString(), equalTo(expectedEndState.normaliseJsonString()))
        assertThat(jobResult, equalTo(
            "Carla Azar is cleaning this week - an email has been sent to all members.\n" +
                "Current state has been stored in Dropbox")
        )
    }

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
                config.get(FROM_ADDRESS(jobName)),
                config.get(FROM_FULLNAME(jobName))
            ),
            to = listOf(InternetAddress(nextUpEmailAddress, nextUpName)),
            bcc = listOf(InternetAddress(config.get(BCC_ADDRESS(jobName)))),
            subject = CompiledTemplate.from(RawTemplate(config.get(SUBJECT_B(jobName))), emailModel).value,
            body = CompiledTemplate.from(RawTemplate(config.get(BODY_B(jobName)) + config.get(FOOTER(jobName))), emailModel).value
        ).toGmailMessage()

        val expectedEndState =
          """
          |{
          |  "status": "NOT_CLEANING_THIS_WEEK",
          |  "nextUp": {
          |    "name": "$nextUpName",
          |    "email": "$nextUpEmailAddress"
          |  },
          |  "lastRanOn": "2018-06-04",
          |  "emailContents": "body B with Milford<br>some footer"
          |}
          |""".trimMargin()

        val appStateDatastore = DropboxDatastore(dropboxClient, appStateMetadata)
        val jobResult = CleaningRotaGmailer(gmailClient, appStateDatastore, DropboxDatastore(dropboxClient, membersMetadata), config).run(time)

        assertEmailEqual(gmailClient.sentMail.last(), expectedEmail)
        assertThat(appStateDatastore.currentApplicationState().expectSuccess().asJsonString(), equalTo(expectedEndState.normaliseJsonString()))
        assertThat(jobResult, equalTo(
            "There is no cleaning this week - an email reminder has been sent to Milford who is cleaning next week.\n" +
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
        val jobResult = CleaningRotaGmailer(gmailClient, failingAppStateDatastore, successfulMembersDatastore, config).run(time)

        assertThat(gmailClient.sentMail, equalTo(emptyList<Message>()))
        assertThat(jobResult, equalTo("Error downloading file /cleaning_rota_gmailer.json from Dropbox"))
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
        val jobResult = CleaningRotaGmailer(gmailClient, successfulAppStateDatastore, failingMembersDatastore, config).run(time)

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
        val jobResult = CleaningRotaGmailer(gmailClient, appDatastore, membersDatastore, config).run(time)

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
                        .apply { removeAndSet(RUN_ON_DAYS(jobName), "Monday, Wednesday, Thursday") }
                        .toMap()
        )
        val jobResult = CleaningRotaGmailer(gmailClient, DropboxDatastore(dropboxClient, appStateMetadata), DropboxDatastore(dropboxClient, membersMetadata), localConfig).run(firstOfJune)

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
        val beforeTenThirty = ZonedDateTime.of(2018, 6, 4, 7, 14, 59, 0, ZoneOffset.UTC)
        val localConfig = config.copy(
                config = configValues.toMutableMap()
                        .apply { removeAndSet(RUN_AFTER_TIME(jobName), "04:15") }
                        .apply { removeAndSet(RUN_AFTER_TZDB(jobName), "America/Sao_Paulo") }
                        .toMap()
        )
        val jobResult = CleaningRotaGmailer(gmailClient, DropboxDatastore(dropboxClient, appStateMetadata), DropboxDatastore(dropboxClient, membersMetadata), localConfig).run(beforeTenThirty)

        assertThat(jobResult, equalTo("No need to run - time is 04:14 in America/Sao_Paulo, only running after 04:15 in America/Sao_Paulo"))
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

        val jobResult = CleaningRotaGmailer(StubGmailClient(emptyList()), DropboxDatastore(dropboxClient, appStateMetadata), DropboxDatastore(dropboxClient, membersMetadata), config).run(time)

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

        val jobResult = CleaningRotaGmailer(StubGmailClient(emptyList()), DropboxDatastore(dropboxClient, appStateMetadata), DropboxDatastore(dropboxClient, membersMetadata), config).run(time)

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
