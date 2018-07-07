package config

import GmailBot.Companion.RequiredConfig
import java.io.File
import java.nio.file.Path

typealias Configuration = Map<RequiredConfig, String>

enum class ConfigMethod(val tryToGetConfig: (RequiredConfig, Path?) -> String?) {
    FileStorage(getFromFile),
    EnvironmentVariables(getFromEnvVar)
}

val getFromFile = fun (requiredConfig: RequiredConfig, configFileDir: Path?): String? = try {
    File(configFileDir.toString() + File.separator + requiredConfig.name.toLowerCase()).readText()
} catch (e: Exception) {
    null
}

val getFromEnvVar = fun (requiredConfig: RequiredConfig, _: Path?): String? = try {
    System.getenv(requiredConfig.name)
} catch (e: Exception) {
    null
}

object Configurator {
    operator fun invoke(requiredConfig: List<RequiredConfig>, configFileDir: Path?): Configuration {

        val foundConfig = requiredConfig.map { required ->
            fun lookForConfig(tried: List<ConfigMethod> = emptyList()): Pair<RequiredConfig, String?> {
                val methodToTry: ConfigMethod? = ConfigMethod.values().toList().find { tried.contains(it).not() }

                return when {
                    methodToTry != null -> {
                        val config = methodToTry.tryToGetConfig(required, configFileDir)
                        when {
                            config != null -> required to config
                            else -> lookForConfig(tried.plus(methodToTry))
                        }
                    }
                    else -> required to null
                }
            }

            lookForConfig()
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
