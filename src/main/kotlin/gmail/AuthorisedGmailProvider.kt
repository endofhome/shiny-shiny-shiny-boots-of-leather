package gmail

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import config.Configuration
import jobs.GmailForwarder.Companion.RequiredConfig.KOTLIN_GMAILER_GMAIL_ACCESS_TOKEN
import jobs.GmailForwarder.Companion.RequiredConfig.KOTLIN_GMAILER_GMAIL_CLIENT_SECRET
import jobs.GmailForwarder.Companion.RequiredConfig.KOTLIN_GMAILER_GMAIL_REFRESH_TOKEN

class AuthorisedGmailProvider(port: Int, private val appName: String, private val config: Configuration) {

    private val useAccessTokenFromConfig = true
    private val jsonFactory = JacksonFactory.getDefaultInstance()
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

    fun gmail(): Gmail {
        return Gmail.Builder(httpTransport, jsonFactory, credentials)
                    .setApplicationName(appName)
                    .build()
    }

    private val credentials = when (useAccessTokenFromConfig) {
        true  -> credentialFromConfig()
        false -> newCredentials(httpTransport, port)
    }

    private fun credentialFromConfig(): Credential {
        val clientSecret = config.get(KOTLIN_GMAILER_GMAIL_CLIENT_SECRET)
        val clientSecrets: GoogleClientSecrets = GoogleClientSecrets.load(jsonFactory, clientSecret.reader())
        val credential = GoogleCredential.Builder()
                                                         .setTransport(httpTransport)
                                                         .setJsonFactory(jsonFactory)
                                                         .setClientSecrets(clientSecrets)
                                                         .build()
        credential.accessToken = config.get(KOTLIN_GMAILER_GMAIL_ACCESS_TOKEN)
        credential.refreshToken = config.get(KOTLIN_GMAILER_GMAIL_REFRESH_TOKEN)
        return credential
    }

    private fun newCredentials(httpTransport: NetHttpTransport, port: Int): Credential {
        val clientSecret = config.get(KOTLIN_GMAILER_GMAIL_CLIENT_SECRET)
        val credentialsFolder = config.configDir?.toFile()
        val scopes = listOf(GmailScopes.MAIL_GOOGLE_COM)
        val clientSecrets: GoogleClientSecrets = GoogleClientSecrets.load(jsonFactory, clientSecret.reader())

        val flow = GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, scopes)
                .setDataStoreFactory(FileDataStoreFactory(credentialsFolder))
                .setAccessType("offline")
                .build()

        val localServerReceiver = LocalServerReceiver.Builder().setPort(port).build()
        return AuthorizationCodeInstalledApp(flow, localServerReceiver).authorize("user")
    }
}
