package jobs.GmailForwarderTest

import com.google.api.services.gmail.model.Message
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.Configuration
import config.RequiredConfigItem
import datastore.DropboxWriteFailure
import datastore.ErrorDownloadingFileFromDropbox
import datastore.SimpleDropboxClient
import gmail.SimpleGmailClient
import jobs.GmailForwarderJob.GmailForwarder
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfig
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfigItem
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfigItem.GMAIL_FORWARDER_BCC_ADDRESS
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfigItem.GMAIL_FORWARDER_FROM_ADDRESS
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfigItem.GMAIL_FORWARDER_GMAIL_QUERY
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfigItem.GMAIL_FORWARDER_RUN_ON_DAYS
import jobs.GmailForwarderJob.GmailForwarder.Companion.GmailForwarderConfigItem.GMAIL_FORWARDER_TO_ADDRESS
import org.junit.Test
import result.CouldNotSendEmail
import result.Result
import result.Result.Failure
import result.Result.Success
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

class GmailForwarderTest {

    private val time = ZonedDateTime.of(2018, 6, 1, 0, 0, 0, 0, UTC)
    private val baseConfigValues = GmailForwarderConfig().values().associate { it to "unused" }.toMutableMap()
    private val configValues: Map<GmailForwarderConfigItem, String> = baseConfigValues.apply {
        set(GMAIL_FORWARDER_RUN_ON_DAYS, "1")
        set(GMAIL_FORWARDER_TO_ADDRESS, "jim@example.com")
        set(GMAIL_FORWARDER_FROM_ADDRESS, "bob@example.com")
        set(GMAIL_FORWARDER_BCC_ADDRESS, "fred@example.com")
    }.toMap()
    @Suppress("UNCHECKED_CAST")
    private val config = Configuration(configValues as Map<RequiredConfigItem, String>, GmailForwarderConfig(), null)

    @Test
    fun `Happy path`() {
        val state =
        """
          |{
          |  "lastEmailSent": "${time.minusMonths(1)}",
          |  "emailContents": "Last month's email data"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/gmailer_state.json", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw("New email data"))
        val jobResult = GmailForwarder(StubGmailClient(emails), dropboxClient, config).run(time)
        assertThat(jobResult, equalTo(
                "New email has been sent\n" +
                "Current state has been stored in Dropbox")
        )
    }

    @Test
    fun `Email isn't sent if one has already been sent this month`() {
        val state =
                """
          |{
          |  "lastEmailSent": "${time.withDayOfMonth(1)}",
          |  "emailContents": "Fairly new email data"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/gmailer_state.json", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw("New email data"))
        val jobResult = GmailForwarder(StubGmailClient(emails), dropboxClient, config).run(time)
        assertThat(jobResult, equalTo("Exiting, email has already been sent for June 2018"))
    }

    @Test
    fun `Emails cannot have been sent in the future`() {
        val state =
                """
          |{
          |  "lastEmailSent": "${time.plusSeconds(1)}",
          |  "emailContents": "Next month's email data"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/gmailer_state.json", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw("Last month's email data"))
        val jobResult = GmailForwarder(StubGmailClient(emails), dropboxClient, config).run(time)
        assertThat(jobResult, equalTo("Exiting due to invalid state, previous email appears to have been sent in the future"))
    }

    @Test
    fun `Email isn't sent if the exact same email contents have already been sent`() {
        val state =
          """
          |{
          |  "lastEmailSent": "${time.minusMonths(1)}",
          |  "emailContents": "
          |     From: Bob
          |     To: Jim
          |     ________________________________
          |     Already sent this one"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/gmailer_state.json", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw(
          """
          |     From: Jim
          |     To: Bob
          |     ________________________________
          |     Already sent this one
          """.trimMargin()))
        val jobResult = GmailForwarder(StubGmailClient(emails), dropboxClient, config).run(time)
        assertThat(jobResult, equalTo("Exiting as this exact email has already been sent"))
    }

    @Test
    fun `Email is only sent on a particular day of the month`() {
        val state =
                """
          |{
          |  "lastEmailSent": "${time.minusMonths(1)}",
          |  "emailContents": "Last month's email data"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/gmailer_state.json", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw("New email data"))
        val firstOfJune = ZonedDateTime.of(2018, 6, 1, 0, 0, 0, 0, UTC)
        val localConfig = config.copy(
                config = configValues.toMutableMap()
                                     .apply { set(GMAIL_FORWARDER_RUN_ON_DAYS, "2, 11,12, 31 ") }
                                     .toMap()
        )
        val jobResult = GmailForwarder(StubGmailClient(emails), dropboxClient, localConfig).run(firstOfJune)
        assertThat(jobResult, equalTo("No need to run - day of month is 1, only running on day 2, 11, 12, 31 of each month"))
    }

    @Test
    fun `Error message is provided when emails fail to be sent`() {
        val state =
                """
          |{
          |  "lastEmailSent": "${time.minusMonths(1)}",
          |  "emailContents": "Last month's email data"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/gmailer_state.json", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw("New email data"))
        val jobResult = GmailForwarder(StubGmailClientThatCannotSend(emails), dropboxClient, config).run(time)
        assertThat(jobResult, equalTo("Error - could not send email/s"))
    }

    @Test
    fun `Error message is provided when state cannot be stored in Dropbox`() {
        val state =
                """
          |{
          |  "lastEmailSent": "${time.minusMonths(1)}",
          |  "emailContents": "Last month's email data"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/gmailer_state.json", state)

        val dropboxClient = StubDropboxClientThatCannotStore(listOf(stateFile))
        val emails = listOf(Message().setRaw("New email data"))
        val jobResult = GmailForwarder(StubGmailClient(emails), dropboxClient, config).run(time)
        assertThat(jobResult, equalTo("New email has been sent\nError - could not store state in Dropbox"))
    }

    @Test
    fun `Error message is provided when email raw content cannot be retrieved from Gmail`() {
        val state =
                """
          |{
          |  "lastEmailSent": "${time.minusMonths(1)}",
          |  "emailContents": "Last month's email data"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/gmailer_state.json", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw("New email data"))
        val jobResult = GmailForwarder(StubGmailClientThatCannotRetrieveRawContent(emails), dropboxClient, config).run(time)
        assertThat(jobResult, equalTo("Error - could not get raw message content for email"))
    }

    @Test
    fun `Error message is provided when there are no matches for search query`() {
        val state =
          """
          |{
          |  "lastEmailSent": "${time.minusMonths(1)}",
          |  "emailContents": "Last month's email data"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/gmailer_state.json", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val localConfig = config.copy(
                config = configValues.toMutableMap()
                                     .apply { set(GMAIL_FORWARDER_GMAIL_QUERY, "some search query") }
                                     .toMap()
        )
        val jobResult = GmailForwarder(StubGmailClientThatReturnsNoMatches(emptyList()), dropboxClient, localConfig).run(time)
        assertThat(jobResult, equalTo("No matching results for query: 'some search query'"))
    }

    @Test
    fun `Error message is provided when state file does not exist in Dropbox`() {
        val dropboxClient = StubDropboxClient(emptyList())
        val emails = listOf(Message().setRaw("New email data"))
        val jobResult = GmailForwarder(StubGmailClient(emails), dropboxClient, config).run(time)
        assertThat(jobResult, equalTo("Error downloading file /gmailer_state.json from Dropbox"))
    }
}

open class StubGmailClient(private val emails: List<Message>) : SimpleGmailClient {
    val sentMail = mutableListOf<Message>()

    override fun lastEmailForQuery(queryString: String): Message? {
        return emails.last()
    }

    override fun rawContentOf(cookedMessage: Message): ByteArray? =
            cookedMessage.raw.toByteArray()

    override fun send(message: Message): Result<CouldNotSendEmail, Message> {
        sentMail.add(message)
        return Success(message)
    }
}

class StubGmailClientThatCannotSend(emails: List<Message>) : StubGmailClient(emails) {
    override fun send(message: Message): Result<CouldNotSendEmail, Message> = Failure(CouldNotSendEmail())
}

class StubGmailClientThatCannotRetrieveRawContent(emails: List<Message>) : StubGmailClient(emails) {
    override fun rawContentOf(cookedMessage: Message): ByteArray? = null
}

class StubGmailClientThatReturnsNoMatches(emails: List<Message>) : StubGmailClient(emails) {
    override fun lastEmailForQuery(queryString: String): Message? = null
}


open class StubDropboxClient(initialFiles: List<FileLike>) : SimpleDropboxClient {
    private var files = initialFiles

    override fun readFile(filename: String): Result<ErrorDownloadingFileFromDropbox, String> {
        val fileMaybe = files.find { it.name == filename }
        return fileMaybe?.let { fileLike ->
            Success(fileLike.contents)
        } ?: Failure(ErrorDownloadingFileFromDropbox(filename))
    }

    override fun writeFile(fileContents: String, filename: String, fileDescription: String): Result<DropboxWriteFailure, String> {
        files += FileLike(filename, fileContents)
        return Success("$fileDescription\nCurrent state has been stored in Dropbox")
    }
}

class StubDropboxClientThatCannotStore(initialFiles: List<FileLike>) : StubDropboxClient(initialFiles) {
    override fun writeFile(fileContents: String, filename: String, fileDescription: String): Result<DropboxWriteFailure, String> =
            Failure(DropboxWriteFailure(fileDescription))
}

data class FileLike(val name: String, val contents: String)
