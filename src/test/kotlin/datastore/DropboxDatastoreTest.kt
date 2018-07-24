package datastore

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import jobs.GmailForwarderTest.FileLike
import jobs.GmailForwarderTest.StubDropboxClient
import org.junit.Test
import result.Result
import result.Result.Success

class DropboxDatastoreTest {

    data class TestAppState(val someProperty: String) : ApplicationState

    @Test
    fun `correct state is stored`() {
        val metadata = FlatFileApplicationStateMetadata("stateFile", TestAppState::class.java)
        val initialFiles: List<FileLike> = emptyList()
        val dropboxDatastore = DropboxDatastore(StubDropboxClient(initialFiles), metadata)
        val testAppState = TestAppState("some new state")

        dropboxDatastore.store(testAppState, "")

        assertThat(dropboxDatastore.currentApplicationState().expectSuccess(), equalTo(testAppState))
    }
}

fun <F, S> Result<F, S>.expectSuccess() = (this as Success).value