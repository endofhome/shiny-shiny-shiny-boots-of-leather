import MainConfigItem.MAIN_GMAIL_FORWARDER_JOB_NAME
import config.Configurator
import config.FormattedJobName
import config.RequiredConfig
import config.RequiredConfigItem
import jobs.GmailForwarderJob.GmailForwarder
import jobs.GmailForwarderJob.GmailForwarderConfig
import jobs.Job
import java.nio.file.Paths
import java.time.ZonedDateTime

fun main(args: Array<String>) {
    val mainConfig = Configurator(MainConfig(), Paths.get("credentials"))
    val mainJobName = mainConfig.requiredConfig.formattedJobName
    val gmailForwarderConfig = GmailForwarderConfig(mainConfig.get(MAIN_GMAIL_FORWARDER_JOB_NAME(mainJobName)))

    val gmailForwarder = GmailForwarder.initialise(gmailForwarderConfig)
    val jobs: List<Job> = listOf(
            gmailForwarder
    )

    jobs.forEach { job ->
        val result = job.run(ZonedDateTime.now())
        println("${job.jobName.value}: $result")
    }
}

class MainConfig : RequiredConfig("Shiny Shiny Shiny Boots of Leather") {
    override fun values(): Set<RequiredConfigItem> =
        setOf(
            MAIN_GMAIL_FORWARDER_JOB_NAME(formattedJobName)
        )
}

sealed class MainConfigItem(override val name: String) : RequiredConfigItem {
    class MAIN_GMAIL_FORWARDER_JOB_NAME(jobName: FormattedJobName) : MainConfigItem("${jobName.value}_GMAIL_FORWARDER_JOB_NAME")
}