import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import java.io.BufferedReader
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object AuthorisedGmailProvider {
    private const val applicationName = "Kotlin Gmailer Bot"
    private val jsonFactory = JacksonFactory.getDefaultInstance()
    private val credentialsFolder = File("credentials")
    private val clientSecretPath = Paths.get(credentialsFolder.path + File.separator + "client_secret.json")
    private val scopes = listOf(GmailScopes.MAIL_GOOGLE_COM)

    operator fun invoke(port: Int): Gmail {
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
