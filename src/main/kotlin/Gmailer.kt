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
    fun newMessageFrom(emailBytes: ByteArray?): MimeMessage?
    fun rawMessageContent(cookedMessage: Message): ByteArray?
}

class RealGmailer(private val gmail: Gmail) {
    private val user = "me"

    fun messages(): Messages {
        return gmail.users().messages()
    }

    fun lastEmailForQuery(queryString: String): Message? {
        val messages: Messages = messages()
        val listResponse: ListMessagesResponse? = messages.list(user).setQ(queryString).execute()

        // TODO: get the most recently received email, assuming it was for the month in question.
        return listResponse?.messages?.firstOrNull()
    }

    fun send(message: Message): Message? {
        return messages().send(user, message).execute()
    }

    fun newMessageFrom(emailBytes: ByteArray?): MimeMessage {
        val props = Properties()
        val session: Session? = Session.getDefaultInstance(props, null)
        return MimeMessage(session, ByteArrayInputStream(emailBytes))
    }

    fun rawMessageContent(cookedMessage: Message): ByteArray? {
        val message: Message? = gmail.users().messages().get(user, cookedMessage.id).setFormat("raw").execute()
        return message?.let {
            Base64(true).decode(message.raw)
        }
    }
}

open class StubGmailer(private val emails: List<Message>) : Gmailer {
    override fun lastEmailForQuery(queryString: String): Message? {
        return emails.last()
    }

    override fun newMessageFrom(emailBytes: ByteArray?): MimeMessage? {
        val props = Properties()
        val sessionMaybe: Session? = Session.getDefaultInstance(props, null)
        return sessionMaybe?.let { session ->
            MimeMessage(session, ByteArrayInputStream(emailBytes))
        }
    }

    override fun rawMessageContent(cookedMessage: Message): ByteArray? =
            cookedMessage.raw.toByteArray()

    override fun send(message: Message): Message? = Message()
}

class StubGmailerThatCannotSend(emails: List<Message>) : StubGmailer(emails) {
    override fun send(message: Message): Message? = null
}

class StubGmailerThatCannotRetrieveRawContent(emails: List<Message>) : StubGmailer(emails) {
    override fun rawMessageContent(cookedMessage: Message): ByteArray? = null
}

data class ApplicationState<T>(val state: T)
data class GmailerState(val lastEmailSent: ZonedDateTime, val emailContents: String)
