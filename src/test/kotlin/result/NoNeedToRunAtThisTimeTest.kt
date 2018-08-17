package result

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NoNeedToRunAtThisTimeTest {

    @Test
    fun `minutes are padded`() {
        val zoneId = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(2018, 3, 4, 5, 0, 0, 0, zoneId)
        val error = NoNeedToRunAtThisTime(now, now, zoneId)

        assertThat(error.message, equalTo("No need to run - time is 05:00 in Europe/London, only running after 05:00 in Europe/London"))
    }
}