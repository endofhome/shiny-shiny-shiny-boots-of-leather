import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.Gmail.Users.Messages
import com.google.api.services.gmail.model.ListMessagesResponse
import com.google.api.services.gmail.model.Message
import java.io.ByteArrayInputStream
import java.time.ZonedDateTime
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeMessage

interface Gmailer {
    fun lastEmailForQuery(queryString: String): Message?
    fun send(message: Message): Message?
    fun rawContentOf(cookedMessage: Message): ByteArray?
    fun newMessageFrom(emailBytes: ByteArray?): MimeMessage? {
        val props = Properties()
        val sessionMaybe: Session? = Session.getDefaultInstance(props, null)
        return sessionMaybe?.let { session ->
            MimeMessage(session, ByteArrayInputStream(emailBytes))
        }
    }
}

class HttpGmailer(private val gmail: Gmail) : Gmailer {
    private val user = "me"

    override fun lastEmailForQuery(queryString: String): Message? {
        val messages: Messages = messages()
        val listResponse: ListMessagesResponse? = messages.list(user).setQ(queryString).execute()

        // TODO: get the most recently received email, assuming it was for the month in question.
        return listResponse?.messages?.firstOrNull()
    }

    override fun send(message: Message): Message? {
        return messages().send(user, message).execute()
    }

    override fun rawContentOf(cookedMessage: Message): ByteArray? {
        val message: Message? = gmail.users().messages().get(user, cookedMessage.id).setFormat("raw").execute()
        return message?.let {
            Base64(true).decode(message.raw)
        }
    }

    private fun messages(): Messages {
        return gmail.users().messages()
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

data class ApplicationState<T>(val state: T)
data class GmailerState(val lastEmailSent: ZonedDateTime, val emailContents: String)
