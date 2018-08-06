package result

import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

interface Err { val message: String }
interface NoNeedToRun : Err

class NoNeedToRunOnThisDay(dayOfMonth: Int, daysOfMonthToRun: List<Int>) : Err, NoNeedToRun  {
    override val message = "No need to run - day of month is $dayOfMonth, only running on day ${daysOfMonthToRun.joinToString(", ")} of each month"
}

class NoNeedToRunAtThisTime(now: LocalTime, timeToRunAfter: LocalTime) : Err, NoNeedToRun  {
    override val message = "No need to run - time is ${now.hour}:${now.minute}, only running after ${timeToRunAfter.hour}:${timeToRunAfter.minute}"
}

class InvalidStateInFuture : Err {
    override val message = "Exiting due to invalid state, previous email appears to have been sent in the future"
}

class ThisEmailAlreadySent : Err {
    override val message = "Exiting as this exact email has already been sent"
}

class AnEmailAlreadySentThisMonth(now: ZonedDateTime) : Err {
    override val message = "Exiting, email has already been sent for ${now.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${now.year}"
}

class NoMatchingResultsForQuery(queryString: String) : Err {
    override val message = "No matching results for query: '$queryString'"
}

class CouldNotGetRawContentForEmail : Err {
    override val message = "Error - could not get raw message content for email"
}

class ErrorDecoding(messageSuffix: String? = null) : Err {
    override val message = "Error - could not decode raw message${messageSuffix?.let { ", $it" }}"
}

class CouldNotSendEmail : Err {
    override val message = "Error - could not send email/s"
}

class NotAListOfEmailAddresses(string: String) : Err {
    override val message = "Error - $string is not a list of valid email address"
}

class UnknownError : Err {
    override val message = "Exiting due to unknown error"
}
