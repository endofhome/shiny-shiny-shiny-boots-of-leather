package acceptance

import GmailBot
import com.google.api.services.gmail.model.Message
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import datastore.SimpleDropboxClient
import datastore.WriteState
import gmail.Gmailer
import org.junit.Test
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

class GmailerBotAcceptanceTest {

    private val time = ZonedDateTime.of(2018, 6, 1, 0, 0, 0, 0, UTC)

    @Test
    fun `Happy path`() {
        val state =
        """
          |{
          |  "lastEmailSent": "${time.minusMonths(1)}",
          |  "emailContents": "Last month's email data"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/gmailer_state", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw("New email data"))
        val jobResult = GmailBot(StubGmailer(emails), dropboxClient).run(time, listOf(1))
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
        val stateFile = FileLike("/gmailer_state", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw("New email data"))
        val jobResult = GmailBot(StubGmailer(emails), dropboxClient).run(time, listOf(1))
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
        val stateFile = FileLike("/gmailer_state", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw("Last month's email data"))
        val jobResult = GmailBot(StubGmailer(emails), dropboxClient).run(time, listOf(1))
        assertThat(jobResult, equalTo("Exiting due to invalid state, previous email appears to have been sent in the future"))
    }

    @Test
    fun `Email isn't sent if the exact same email contents have already been sent`() {
        val state =
                """
          |{
          |  "lastEmailSent": "${time.minusMonths(1)}",
          |  "emailContents": "Already sent this one"
          |}
          |""".trimMargin()
        val stateFile = FileLike("/gmailer_state", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw("Already sent this one"))
        val jobResult = GmailBot(StubGmailer(emails), dropboxClient).run(time, listOf(1))
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
        val stateFile = FileLike("/gmailer_state", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw("New email data"))
        val secondOfJune = ZonedDateTime.of(2018, 6, 1, 0, 0, 0, 0, UTC)
        val jobResult = GmailBot(StubGmailer(emails), dropboxClient).run(secondOfJune, listOf(2, 11, 12, 31))
        assertThat(jobResult, equalTo("No need to run: day of month is: 1, only running on day 2, 11, 12, 31 of each month"))
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
        val stateFile = FileLike("/gmailer_state", state)

        val dropboxClient = StubDropboxClient(listOf(stateFile))
        val emails = listOf(Message().setRaw("New email data"))
        val jobResult = GmailBot(StubGmailerThatCannotSend(emails), dropboxClient).run(time, listOf(1))
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
        val stateFile = FileLike("/gmailer_state", state)

        val dropboxClient = StubDropboxClientThatCannotStore(listOf(stateFile))
        val emails = listOf(Message().setRaw("New email data"))
        val jobResult = GmailBot(StubGmailer(emails), dropboxClient).run(time, listOf(1))
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
        val stateFile = FileLike("/gmailer_state", state)

        val dropboxClient = StubDropboxClientThatCannotStore(listOf(stateFile))
        val emails = listOf(Message().setRaw("New email data"))
        val jobResult = GmailBot(StubGmailerThatCannotRetrieveRawContent(emails), dropboxClient).run(time, listOf(1))
        assertThat(jobResult, equalTo("Error - could not get raw message content for email"))
    }
}

open class StubGmailer(private val emails: List<Message>) : Gmailer {
    override fun lastEmailForQuery(queryString: String): Message? {
        return emails.last()
    }

    override fun rawContentOf(cookedMessage: Message): ByteArray? =
            cookedMessage.raw.toByteArray()

    override fun send(message: Message): Message? = Message()
}

class StubGmailerThatCannotSend(emails: List<Message>) : StubGmailer(emails) {
    override fun send(message: Message): Message? = null
}

class StubGmailerThatCannotRetrieveRawContent(emails: List<Message>) : StubGmailer(emails) {
    override fun rawContentOf(cookedMessage: Message): ByteArray? = null
}


open class StubDropboxClient(initialFiles: List<FileLike>) : SimpleDropboxClient {
    private var files = initialFiles

    override fun readFile(filename: String): String {
        val fileMaybe = files.find { it.name == filename }
        return fileMaybe?.contents ?: ""
    }

    override fun writeFile(fileContents: String, filename: String): WriteState {
        files += FileLike(filename, fileContents)
        return WriteState.Success()
    }
}

class StubDropboxClientThatCannotStore(initialFiles: List<FileLike>) : StubDropboxClient(initialFiles) {
    override fun writeFile(fileContents: String, filename: String): WriteState = WriteState.Failure()
}

data class FileLike(val name: String, val contents: String)