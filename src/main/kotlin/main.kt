import config.Configurator
import datastore.HttpDropboxClient
import gmail.AuthorisedGmailProvider
import gmail.HttpGmailClient
import jobs.GmailBot
import java.nio.file.Paths
import java.time.ZonedDateTime

fun main(args: Array<String>) {
    val requiredConfig: List<GmailBot.Companion.RequiredConfig> = GmailBot.Companion.RequiredConfig.values().toList()
    val config = Configurator(requiredConfig, Paths.get("credentials"))
    val gmail = AuthorisedGmailProvider(4000, GmailBot.appName, config).gmail()
    val gmailer = HttpGmailClient(gmail)
    val dropboxClient = HttpDropboxClient(GmailBot.appName, config.get(GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_DROPBOX_ACCESS_TOKEN))
    val runOnDays = config.getAsListOfInt(GmailBot.Companion.RequiredConfig.KOTLIN_GMAILER_RUN_ON_DAYS)
    val result = GmailBot(gmailer, dropboxClient, config).run(ZonedDateTime.now(), runOnDays)
    println(result)
}