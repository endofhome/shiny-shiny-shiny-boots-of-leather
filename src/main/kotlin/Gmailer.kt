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
import java.io.File.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties
import javax.mail.Message.RecipientType.*
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class Gmailer(private val port: Int,
              private val qString: String,
              private val fromEmail: InternetAddress,
              private val toEmail: InternetAddress
) {
    private val applicationName = "Kotlin Gmailer Bot"
    private val jsonFactory = JacksonFactory.getDefaultInstance()
    private val credentialsFolder = File("credentials")
    private val scopes = listOf(GmailScopes.MAIL_GOOGLE_COM)
    private val clientSecretPath = Paths.get(credentialsFolder.path + separator + "client_secret.json")

    fun findLastEmailWithSubjectAndReSendUsingNewDetails() {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val service = Gmail.Builder(httpTransport, jsonFactory, getCredentials(httpTransport))
                .setApplicationName(applicationName)
                .build()

        val user = "me"
        val messages: Messages = service.users().messages()
        val listResponse = messages.list(user).setQ(qString).execute()
        // TODO: get the most recently received email, assuming it was for the month in question.
        val firstMatch: Message = listResponse.messages.first()
        val message = service.users().messages().get(user, firstMatch.id).setFormat("raw").execute()
        val base64Url = Base64(true)
        val emailBytes = base64Url.decode(message.raw)

        val props = Properties()
        val session = Session.getDefaultInstance(props, null)
        val email: MimeMessage = MimeMessage(session, ByteArrayInputStream(emailBytes))
        email.setFrom(fromEmail)
        email.setRecipient(TO, toEmail)
        val buffer = ByteArrayOutputStream()
        email.writeTo(buffer)
        val bytes = buffer.toByteArray()
        val encodedEmail = Base64.encodeBase64URLSafeString(bytes)
        val messageWithNewSendingData: Message = Message().setRaw(encodedEmail)

        messages.send(user, messageWithNewSendingData).execute()
    }

    private fun getCredentials(httpTransport: NetHttpTransport): Credential {
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
