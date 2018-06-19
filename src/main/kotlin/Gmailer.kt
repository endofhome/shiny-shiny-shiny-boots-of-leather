import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.Gmail.Users.Messages
import com.google.api.services.gmail.model.Message
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Message.RecipientType.TO
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class Gmailer(private val gmail: Gmail) {
    private val user = "me"

    fun messages(): Messages {
        return gmail.users().messages()
    }

    fun lastEmailForQuery(queryString: String): Message? {
        val messages: Messages = messages()
        val listResponse = messages.list(user).setQ(queryString).execute()
        // TODO: get the most recently received email, assuming it was for the month in question.
        return listResponse.messages.firstOrNull()
    }

    fun send(message: Message) {
        messages().send(user, message).execute()
    }

    fun newMessageFrom(emailBytes: ByteArray?): MimeMessage {
        val props = Properties()
        val session = Session.getDefaultInstance(props, null)
        return MimeMessage(session, ByteArrayInputStream(emailBytes))
    }

    fun rawMessageContent(firstMatch: Message): ByteArray? {
        val message = gmail.users().messages().get(user, firstMatch.id).setFormat("raw").execute()
        return Base64(true).decode(message.raw)
    }
}

fun MimeMessage.withSender(senderAddress: InternetAddress): MimeMessage {
    this.setFrom(senderAddress)
    return this
}

fun MimeMessage.withRecipient(recipientAddress: InternetAddress): MimeMessage {
    this.setRecipient(TO, recipientAddress)
    return this
}

fun MimeMessage.encode(): Message {
    val buffer = ByteArrayOutputStream()
    this.writeTo(buffer)
    val bytes = buffer.toByteArray()
    val encodedEmail = Base64.encodeBase64URLSafeString(bytes)
    return Message().setRaw(encodedEmail)
}
