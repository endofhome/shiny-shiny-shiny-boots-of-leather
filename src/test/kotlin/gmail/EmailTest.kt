package gmail

import com.google.api.services.gmail.model.Message
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Properties
import javax.mail.Message.RecipientType.BCC
import javax.mail.Message.RecipientType.TO
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64 as GoogleBase64

class EmailTest {
    @Test
    fun `can produce a Gmail Message`() {
        val from = InternetAddress("bob@example.com", "Bob")
        val to = listOf(InternetAddress("jim@example.com", "Jim"))
        val bcc = listOf(InternetAddress("harry@example.com", "Harry"))
        val subject = "Incredible deals"
        val body = "Wow, amazement, incredible"
        val email = Email(from, to, bcc, subject, body)

        val byteArrayOutputStream = ByteArrayOutputStream()
        MimeMessage(Session.getDefaultInstance(Properties())).run {
            setFrom(from)
            addRecipients(TO, to.toTypedArray())
            addRecipients(BCC, bcc.toTypedArray())
            setSubject(subject)
            setText(body)
            writeTo(byteArrayOutputStream)
        }
        val base64String = Base64.getUrlEncoder().encodeToString(byteArrayOutputStream.toByteArray())
        val expectedMessage: Message = Message().setRaw(base64String)
        assertEmailEqual(email.toGmailMessage(), expectedMessage)
    }
}

fun assertEmailEqual(actual: Message, expected: Message) {
    assertThat(actual.decodeRawAsStringWithoutMessageId(), equalTo(expected.decodeRawAsStringWithoutMessageId()))
}
