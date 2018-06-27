package datastore

import acceptance.FileLike
import acceptance.StubDropboxClient
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import gmail.ApplicationState
import org.junit.Test

class DropboxDatastoreTest {

    data class TestAppState(val someProperty: String)

    @Test
    fun `correct state is stored`() {
        val metadata = FlatFileApplicationStateMetadata("stateFile", TestAppState::class.java)
        val initialFiles: List<FileLike> = emptyList()
        val dropboxDatastore = DropboxDatastore(StubDropboxClient(initialFiles), metadata)
        val testAppState = TestAppState("some new state")
        val newAppState = ApplicationState(testAppState)

        dropboxDatastore.store(newAppState)

        assertThat(dropboxDatastore.currentApplicationState(), equalTo(newAppState))
    }

}