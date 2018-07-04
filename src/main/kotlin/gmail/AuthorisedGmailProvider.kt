package gmail

import GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_GMAIL_CLIENT_SECRET
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.load
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import config.Configuration
import java.io.File

class AuthorisedGmailProvider(private val port: Int, appName: String, private val config: Configuration) {
    private val applicationName = appName
    private val jsonFactory = JacksonFactory.getDefaultInstance()
    private val credentialsFolder = File("credentials")
    private val scopes = listOf(GmailScopes.MAIL_GOOGLE_COM)

    fun gmail(): Gmail {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        return Gmail.Builder(httpTransport, jsonFactory, getCredentials(httpTransport, port))
                .setApplicationName(applicationName)
                .build()
    }

    private fun getCredentials(httpTransport: NetHttpTransport, port: Int): Credential {
        val clientSecrets: GoogleClientSecrets = load(jsonFactory, config[KOTLIN_GMAILER_GMAIL_CLIENT_SECRET]!!.reader())

        val flow = GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, scopes)
                .setDataStoreFactory(FileDataStoreFactory(credentialsFolder))
                .setAccessType("offline")
                .build()

        val localServerReceiver = LocalServerReceiver.Builder().setPort(port).build()
        return AuthorizationCodeInstalledApp(flow, localServerReceiver).authorize("user")
    }
}
