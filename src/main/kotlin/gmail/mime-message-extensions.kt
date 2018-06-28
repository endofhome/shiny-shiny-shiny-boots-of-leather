package gmail

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64
import com.google.api.services.gmail.model.Message
import java.io.ByteArrayOutputStream
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

fun MimeMessage.replaceSender(senderAddress: InternetAddress): MimeMessage {
    this.setFrom(senderAddress)
    return this
}

fun MimeMessage.replaceRecipient(recipientAddress: InternetAddress): MimeMessage {
    this.setRecipient(javax.mail.Message.RecipientType.TO, recipientAddress)
    return this
}

fun MimeMessage.encode(): Message {
    val buffer = ByteArrayOutputStream()
    this.writeTo(buffer)
    val bytes = buffer.toByteArray()
    val encodedEmail = Base64.encodeBase64URLSafeString(bytes)
    return Message().setRaw(encodedEmail)
}