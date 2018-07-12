package gmail

import Err
import Result
import Result.Failure
import Result.Success
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.Gmail.Users.Messages
import com.google.api.services.gmail.model.ListMessagesResponse
import com.google.api.services.gmail.model.Message
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.ZonedDateTime
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeMessage

interface SimpleGmailClient {
    fun lastEmailForQuery(queryString: String): Message?
    fun send(message: Message): Result<CouldNotSendEmail, Message>
    fun rawContentOf(cookedMessage: Message): ByteArray?
    fun newMessageFrom(emailBytes: ByteArray?): MimeMessage =
            MimeMessage(
                    Session.getDefaultInstance(Properties(), null),
                    ByteArrayInputStream(emailBytes)
            )
}

class HttpGmailClient(private val gmail: Gmail) : SimpleGmailClient {
    private val user = "me"

    override fun lastEmailForQuery(queryString: String): Message? {
        val messages: Messages = messages()
        val listResponse: ListMessagesResponse? = messages.list(user).setQ(queryString).execute()
        return listResponse?.messages?.firstOrNull()
    }

    override fun send(message: Message): Result<CouldNotSendEmail, Message> {
        return try {
            Success(messages().send(user, message).execute())
        } catch (e: Exception) {
            when (e) {
                is IOException -> Failure(CouldNotSendEmail(message))
                else           -> throw e
            }
        }
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

interface ApplicationState
data class GmailerState(val lastEmailSent: ZonedDateTime, val emailContents: String) : ApplicationState
class CouldNotSendEmail(message: Message) : Err {
    override val message = "Error - could not send email/s"
}
