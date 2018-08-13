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

class AuthorisedGmailProvider(port: Int, private val appName: String, private val secrets: GmailSecrets, private val config: Configuration) {

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
        val clientSecrets: GoogleClientSecrets = GoogleClientSecrets.load(jsonFactory, secrets.clientSecret.reader())
        val credential = GoogleCredential.Builder()
                                         .setTransport(httpTransport)
                                         .setJsonFactory(jsonFactory)
                                         .setClientSecrets(clientSecrets)
                                         .build()
        credential.accessToken = secrets.accessToken
        credential.refreshToken = secrets.refreshToken
        return credential
    }

    private fun newCredentials(httpTransport: NetHttpTransport, port: Int): Credential {
        val credentialsFolder = config.configDir?.toFile()
        val scopes = listOf(GmailScopes.MAIL_GOOGLE_COM)
        val clientSecrets: GoogleClientSecrets = GoogleClientSecrets.load(jsonFactory, secrets.clientSecret.reader())

        val flow = GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, scopes)
                .setDataStoreFactory(FileDataStoreFactory(credentialsFolder))
                .setAccessType("offline")
                .build()

        val localServerReceiver = LocalServerReceiver.Builder().setPort(port).build()
        return AuthorizationCodeInstalledApp(flow, localServerReceiver).authorize("user").also {
            println("Google Access Token: ${it.accessToken}")
            println("Google Refresh Token: ${it.refreshToken}")
        }
    }
}

data class GmailSecrets(val clientSecret: String, val accessToken: String, val refreshToken: String)
