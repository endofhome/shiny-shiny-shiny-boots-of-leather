package gmail

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.Gmail.Users.Messages
import com.google.api.services.gmail.model.ListMessagesResponse
import com.google.api.services.gmail.model.Message
import result.CouldNotSendEmail
import result.Result
import result.Result.Failure
import result.Result.Success
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

interface SimpleGmailClient {
    fun lastEmailForQuery(queryString: String): Message?
    fun send(message: Message, subject: String, recipients: List<InternetAddress>): Result<CouldNotSendEmail, Message>
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
        val listResponse: ListMessagesResponse? = messages().list(user).setQ(queryString).execute()
        return listResponse?.messages?.firstOrNull()
    }

    override fun send(message: Message, subject: String, recipients: List<InternetAddress>): Result<CouldNotSendEmail, Message> {
        return try {
            messages().send(user, message).execute()
            Success(message)
        } catch (e: Exception) {
            when (e) {
                is IOException -> Failure(CouldNotSendEmail(subject, recipients))
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

data class Email(val from: InternetAddress, val to: List<InternetAddress>, val bcc: List<InternetAddress>, val subject: String, val body: String) {
    fun toGmailMessage(): Message {
        val fromAddress = this.from
        val emailSubject = this.subject
        val byteArrayOutputStream = ByteArrayOutputStream()
        MimeMessage(Session.getDefaultInstance(Properties())).run {
            setFrom(fromAddress)
            addRecipients(javax.mail.Message.RecipientType.TO, to.toTypedArray())
            addRecipients(javax.mail.Message.RecipientType.BCC, bcc.toTypedArray())
            setSubject(emailSubject)
            setContent(body, "text/html; charset=utf-8; format=flowed")
            writeTo(byteArrayOutputStream)
        }

        val base64String = java.util.Base64.getUrlEncoder().encodeToString(byteArrayOutputStream.toByteArray())
        return Message().run { setRaw(base64String) }
    }
}

fun Message.decodeRawAsStringWithoutMessageId(): String? = decodeRawAsMessageString().withoutMessageIdAsString()

fun Message.decodeRawAsMessageString() = Base64(true).decode(raw).asMessageString()

fun ByteArray.asMessageString() = MessageString(String(this))

data class MessageString(val value: String) {
    fun withoutMessageIdAsString() = this.value.split("\n")
                                               .filterNot { it.startsWith("Message-ID:") }
                                               .map { it.trim() }
                                               .joinToString()
}