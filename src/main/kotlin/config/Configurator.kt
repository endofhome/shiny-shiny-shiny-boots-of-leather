package config

import GmailBot.Companion.RequiredConfig
import java.io.File
import java.nio.file.Path

typealias Configuration = Map<RequiredConfig, String>

object Configurator {
    operator fun invoke(requiredConfig: List<RequiredConfig>, configFileDir: Path?): Configuration {
        val foundConfig = requiredConfig.map {
            if (configFileDir != null) {
                try {
                    val text = File(configFileDir.toString() + File.separator + it.name.toLowerCase()).readText()
                    return@map it to text
                } catch (e: Exception) {}
            }

            try {
                return@map it to System.getenv(it.name)
            } catch (e: Exception) {}

            return@map it to null
        }.toMap()

        return if (foundConfig.containsValue(null)) {
            val missingConfig = foundConfig.filter { it.value == null }.map { it.key }
            throw RuntimeException(
                    "Config value${pluralise(missingConfig)} required for " +
                    "${missingConfig.map { it.name }.joinToString(", ")} " +
                    "but not found"
            )
        } else {
            foundConfig.filterNotNull()
        }
    }

    private fun pluralise(missingConfig: List<RequiredConfig>): String {
        return when {
            missingConfig.size > 1 -> "s"
            else                   -> ""
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterNotNull() = this.filterValues { it != null } as Map<K, V>
