import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.Gmail.Users.Messages
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.File.separator
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties
import javax.mail.Message.RecipientType.TO
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class Gmailer(private val gmail: Gmail) {
    private val user = "me"

    companion object {
        private const val applicationName = "Kotlin Gmailer Bot"
        private val jsonFactory = JacksonFactory.getDefaultInstance()
        private val credentialsFolder = File("credentials")
        private val clientSecretPath = Paths.get(credentialsFolder.path + separator + "client_secret.json")
        private val scopes = listOf(GmailScopes.MAIL_GOOGLE_COM)

        fun initialiseService(port: Int): Gmail {
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            return Gmail.Builder(httpTransport, jsonFactory, getCredentials(httpTransport, port))
                    .setApplicationName(applicationName)
                    .build()
        }

        private fun getCredentials(httpTransport: NetHttpTransport, port: Int): Credential {
            val clientSecretReader: BufferedReader? = Files.newBufferedReader(clientSecretPath)
            val clientSecrets: GoogleClientSecrets = GoogleClientSecrets.load(jsonFactory, clientSecretReader)

            val flow = GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, jsonFactory, clientSecrets, scopes)
                    .setDataStoreFactory(FileDataStoreFactory(credentialsFolder))
                    .setAccessType("offline")
                    .build()

            val localServerReceiver = LocalServerReceiver.Builder().setPort(port).build()
            return AuthorizationCodeInstalledApp(flow, localServerReceiver).authorize("user")
        }
    }


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
