package jobs.NewsletterGmailerJob

import com.github.jknack.handlebars.Handlebars
import com.google.api.services.gmail.model.Message
import config.Configuration
import config.Configurator
import config.RequiredConfig
import config.stringToDayOfWeek
import config.stringToInt
import datastore.ApplicationState
import datastore.DropboxDatastore
import datastore.DropboxWriteFailure
import datastore.ErrorDownloadingFileFromDropbox
import datastore.FlatFileApplicationStateMetadata
import datastore.HttpDropboxClient
import gmail.AuthorisedGmailProvider
import gmail.Email
import gmail.GmailSecrets
import gmail.HttpGmailClient
import gmail.MessageString
import gmail.SimpleGmailClient
import gmail.decodeRawAsStringWithoutMessageId
import jobs.Job
import jobs.JobCompanion
import jobs.NewsletterGmailerJob.NewsletterGmailer.Members
import jobs.NewsletterGmailerJob.NewsletterGmailer.NewsletterGmailerStatus
import jobs.NewsletterGmailerJob.NewsletterGmailer.NewsletterGmailerStatus.CLEANING_THIS_WEEK
import jobs.NewsletterGmailerJob.NewsletterGmailer.NewsletterGmailerStatus.NOT_CLEANING_THIS_WEEK
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.BCC_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.BODY_A
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.BODY_B
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.DROPBOX_ACCESS_TOKEN
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.FOOTER
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.FROM_ADDRESS
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.FROM_FULLNAME
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.GMAIL_ACCESS_TOKEN
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.GMAIL_CLIENT_SECRET
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.GMAIL_REFRESH_TOKEN
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.RUN_AFTER_TIME
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.RUN_AFTER_TZDB
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.RUN_ON_DAYS
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.SUBJECT_A
import jobs.NewsletterGmailerJob.NewsletterGmailerConfigItem.SUBJECT_B
import jobs.NewsletterGmailerJob.TemplatedMessage.CompiledTemplate
import jobs.NewsletterGmailerJob.TemplatedMessage.RawTemplate
import result.AnEmailAlreadySentToday
import result.CouldNotSendEmail
import result.NoNeedToRun
import result.NoNeedToRunAtThisTime
import result.NoNeedToRunOnThisDayOfWeek
import result.NotAListOfEmailAddresses
import result.Result
import result.Result.Failure
import result.Result.Success
import result.ThisEmailAlreadySent
import result.asSuccess
import result.flatMap
import result.map
import result.orElse
import java.nio.file.Paths
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress

class NewsletterGmailer(private val gmailClient: SimpleGmailClient, private val appStateDatastore: DropboxDatastore<NewsletterGmailerState>, private val membersDatastore: DropboxDatastore<Members>, private val config: Configuration): Job {
    override val jobName = config.requiredConfig.formattedJobName

    companion object: JobCompanion {
        override fun initialise(requiredConfig: RequiredConfig): NewsletterGmailer {
            val config = Configurator(requiredConfig, Paths.get("credentials"))
            val jobName = config.requiredConfig.formattedJobName
            val gmailSecrets = GmailSecrets(
                    config.get(GMAIL_CLIENT_SECRET(jobName)),
                    config.get(GMAIL_ACCESS_TOKEN(jobName)),
                    config.get(GMAIL_REFRESH_TOKEN(jobName))
            )
            val gmail = AuthorisedGmailProvider(4000, jobName.value, gmailSecrets, config).gmail()
            val gmailClient = HttpGmailClient(gmail)
            val dropboxClient = HttpDropboxClient(jobName.value, config.get(DROPBOX_ACCESS_TOKEN(jobName)))
            val appStateMetadata = FlatFileApplicationStateMetadata("/newsletter_gmailer.json", NewsletterGmailerState::class.java)
            val appStateDatastore = DropboxDatastore(dropboxClient, appStateMetadata)
            val membersMetadata = FlatFileApplicationStateMetadata("/members2.json", Members::class.java)
            val membersDatastore = DropboxDatastore(dropboxClient, membersMetadata)
            return NewsletterGmailer(gmailClient, appStateDatastore, membersDatastore, config)
        }
    }

    override fun run(now: ZonedDateTime): String =
        shouldRunFor(now).flatMap { ExternalStateRetriever(appStateDatastore, membersDatastore).retrieve() }
                         .flatMap { externalState -> externalState.checkNoEmailSentToday(now.toLocalDate()) }
                         .flatMap { externalState ->
                             when (externalState.appState.status) {
                                 CLEANING_THIS_WEEK     -> notCleaningContext(externalState)
                                 NOT_CLEANING_THIS_WEEK -> cleaningContext(externalState)
                             }
                         }
                         .flatMap { context -> context.validateNotADuplicate() }
                         .flatMap { context -> context.sendAsGmailMessage() }
                         .flatMap { context -> updateAppStateInDb(
                                                   context.toGmailMessage(),
                                                   context.appState,
                                                   context.cleanerOnNotice,
                                                   context.successMessage,
                                                   now) }
                         .orElse { error -> error.message }

    private fun shouldRunFor(now: ZonedDateTime): Result<NoNeedToRun, ZonedDateTime> {
        val daysToRun: List<DayOfWeek> = config.getAsListOf(RUN_ON_DAYS(jobName), stringToDayOfWeek)
        val timeFromConfig = config.getAsListOf(RUN_AFTER_TIME(jobName), stringToInt, ':')
        val timeToRunAfter = LocalTime.of(timeFromConfig[0], timeFromConfig[1])
        val requiredTimeZone = ZoneId.of(config.get(RUN_AFTER_TZDB(jobName)))
        val zonedDateTimeToRunAfter = ZonedDateTime.of(now.toLocalDate(), timeToRunAfter, requiredTimeZone)
        val dayOfWeek = now.dayOfWeek
        val nowInRequiredZone = now.withZoneSameInstant(requiredTimeZone)
        return when {
            daysToRun.contains(dayOfWeek).not()         -> Failure(NoNeedToRunOnThisDayOfWeek(dayOfWeek, daysToRun))
            nowInRequiredZone < zonedDateTimeToRunAfter -> Failure(NoNeedToRunAtThisTime(nowInRequiredZone, zonedDateTimeToRunAfter, requiredTimeZone))
            else                                        -> Success(now)
        }
    }

    private fun ExternalState.checkNoEmailSentToday(now: LocalDate): Result<AnEmailAlreadySentToday, ExternalState> {
        return when {
            now == appState.lastRanOn -> Failure(AnEmailAlreadySentToday())
            else                      -> this.asSuccess()
        }
    }

    private fun cleaningContext(externalState: ExternalState): Success<Context> {
        val cleanerOnNotice = externalState.appState.nextUp
        val emailModel = mapOf("cleaner" to cleanerOnNotice.fullname())
        return Context(
                externalState.appState,
                externalState.members,
                externalState.members.allInternetAddresses(),
                CompiledTemplate.from(RawTemplate(config.get(SUBJECT_A(jobName))), emailModel),
                CompiledTemplate.from(RawTemplate(config.get(BODY_A(jobName)) + config.get(FOOTER(jobName))), emailModel),
                CompiledTemplate.from(RawTemplate("{{cleaner}} is cleaning this week - an email has been sent to all members."), emailModel),
                externalState.members.nextMemberAfter(cleanerOnNotice)
        ).asSuccess()
    }

    private fun notCleaningContext(externalState: ExternalState): Success<Context> {
        val cleanerOnNotice = externalState.appState.nextUp
        val emailModel = mapOf("cleaner" to cleanerOnNotice.name)
        return Context(
                externalState.appState,
                externalState.members,
                listOf(externalState.appState.nextUp.internetAddress()),
                CompiledTemplate.from(RawTemplate(config.get(SUBJECT_B(jobName))), emailModel),
                CompiledTemplate.from(RawTemplate(config.get(BODY_B(jobName)) + config.get(FOOTER(jobName))), emailModel),
                CompiledTemplate.from(RawTemplate("There is no cleaning this week - an email reminder has been sent to {{cleaner}} who is cleaning next week."), emailModel),
                cleanerOnNotice
        ).asSuccess()
    }

    private fun Context.validateNotADuplicate(): Result<ThisEmailAlreadySent, Context> =
        if (thisMessageWasAlreadySent(this.toGmailMessage(), appState.emailContents)) {
            Failure(ThisEmailAlreadySent())
        } else {
            Success(this)
        }

    private fun Context.sendAsGmailMessage(): Result<CouldNotSendEmail, Context> =
        gmailClient.send(this.toGmailMessage(), this.emailSubject.value, this.recipients).map { this }

    private fun updateAppStateInDb(message: Message, appState: NewsletterGmailerState, cleanerOnNotice: Member, successMessage: TemplatedMessage, now: ZonedDateTime): Result<DropboxWriteFailure, String> {
        val nextStatus: NewsletterGmailerStatus = appState.status.flip()
        val cleaner = if (nextStatus == CLEANING_THIS_WEEK) appState.nextUp else null
        val newEmailContents = gmailClient.newMessageFrom(message.decodeRaw()).content.toString()
        val newState = NewsletterGmailerState(nextStatus, cleaner, cleanerOnNotice, now.toLocalDate(), newEmailContents)
        return appStateDatastore.store(newState, successMessage.value)
    }

    private fun thisMessageWasAlreadySent(message: Message, previousEmailContents: String) =
            message.decodeRawAsStringWithoutMessageId() == MessageString(previousEmailContents).asStringWithoutMessageId()

    private fun Context.toGmailMessage() : Message {
        val from = InternetAddress(
                config.get(FROM_ADDRESS(jobName)),
                config.get(FROM_FULLNAME(jobName))
        )
        val to = recipients
        val bccResult = config.get(BCC_ADDRESS(jobName)).toInternetAddresses()
        val bcc = (bccResult as Success).value
        val subject = emailSubject.value
        val body = emailBody.value
        val email = Email(from, to, bcc, subject, body)
        return email.toGmailMessage()
    }

    private fun NewsletterGmailerStatus.flip() = when (this) {
        CLEANING_THIS_WEEK     -> NOT_CLEANING_THIS_WEEK
        NOT_CLEANING_THIS_WEEK -> CLEANING_THIS_WEEK
    }

    private fun String.toInternetAddresses(delimiter: Char = ','): Result<NotAListOfEmailAddresses, List<InternetAddress>> =
        try {
            Success(this.split(delimiter).map { InternetAddress(it, true) })
        } catch (e: Exception) {
            when (e) {
                is AddressException -> Failure(NotAListOfEmailAddresses(this))
                else                -> throw e
            }
        }

    enum class NewsletterGmailerStatus {
        CLEANING_THIS_WEEK,
        NOT_CLEANING_THIS_WEEK
    }

    data class Members(val members: List<Member>): ApplicationState {
        fun allInternetAddresses(): List<InternetAddress> = members.map { it.internetAddress() }
        fun nextMemberAfter(member: Member): Member {
            val membersIterator = members.listIterator(members.indexOf(member) + 1)
            return when {
                membersIterator.hasNext() -> membersIterator.next()
                else                      -> members.first()
            }
        }
    }

    data class Context(
            val appState: NewsletterGmailerState,
            val members: Members,
            val recipients: List<InternetAddress>,
            val emailSubject: TemplatedMessage,
            val emailBody: TemplatedMessage,
            val successMessage: TemplatedMessage,
            val cleanerOnNotice: Member
    )

    class ExternalStateRetriever(private val appStateDatastore: DropboxDatastore<NewsletterGmailerState>, private val membersDatastore: DropboxDatastore<Members>) {
        fun retrieve(): Result<ErrorDownloadingFileFromDropbox, ExternalState> {
            val currentApplicationState = appStateDatastore.currentApplicationState()
            val currentMembers = membersDatastore.currentApplicationState()

            return when {
                currentApplicationState is Failure -> Failure(currentApplicationState.reason)
                currentMembers is Failure          -> Failure(currentMembers.reason)
                else                               -> Success(ExternalState(
                                                          (currentApplicationState as Success).value,
                                                          (currentMembers as Success).value
                                                      ))
            }
        }
    }

}

data class NewsletterGmailerState(
        val status: NewsletterGmailerStatus,
        val cleaner: Member?,
        val nextUp: Member,
        val lastRanOn: LocalDate,
        val emailContents: String
) : ApplicationState

data class Member(val name: String, val surname: String?, val email: String) {
    fun internetAddress(): InternetAddress = InternetAddress(email, fullname())
    fun fullname(): String = "$name${surname?.let { " $it" } ?: ""}"
}

data class ExternalState(val appState: NewsletterGmailerState, val members: Members)

sealed class TemplatedMessage(val value: String) {
    class RawTemplate(message: String): TemplatedMessage(message)
    class CompiledTemplate private constructor(message: String): TemplatedMessage(message) {
        companion object {
            fun from(templatedMessage: TemplatedMessage, model: Map<String, String>): CompiledTemplate =
                when (templatedMessage) {
                    is RawTemplate      -> CompiledTemplate(Handlebars().compileInline(templatedMessage.value).apply(model))
                    is CompiledTemplate -> templatedMessage
                }
        }
    }
}
