package datastore

import Result
import Result.Success
import acceptance.FileLike
import acceptance.StubDropboxClient
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import gmail.ApplicationState
import org.junit.Test

class DropboxDatastoreTest {

    data class TestAppState(val someProperty: String) : ApplicationState

    @Test
    fun `correct state is stored`() {
        val metadata = FlatFileApplicationStateMetadata("stateFile", TestAppState::class.java)
        val initialFiles: List<FileLike> = emptyList()
        val dropboxDatastore = DropboxDatastore(StubDropboxClient(initialFiles), metadata)
        val testAppState = TestAppState("some new state")

        dropboxDatastore.store(testAppState)

        assertThat(dropboxDatastore.currentApplicationState().expectSuccess(), equalTo(testAppState))
    }
}

fun <E, T> Result<E, T>.expectSuccess() = (this as Success).value