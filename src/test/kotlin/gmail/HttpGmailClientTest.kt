package gmail

import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.json.MockJsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Ignore
import org.junit.Test

class HttpGmailClientTest {

    @Ignore("Leaving this here for reference re. stubbing Gmail class, but I'm not sure how to make this work at present.")
    @Test
    fun `GmailClient gets the most relevant email for given query`() {
        val stubGmail: Gmail = Gmail.Builder(MockHttpTransport(), MockJsonFactory(), HttpRequestInitializer(fun (_: HttpRequest) {})).build()
        val httpGmailClient = HttpGmailClient(stubGmail)

        val email = httpGmailClient.lastEmailForQuery("blah")

        assertThat(email, equalTo(Message()))
    }

}
