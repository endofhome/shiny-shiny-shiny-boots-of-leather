package config

import GmailBot.Companion.RequiredConfig
import java.io.File
import java.nio.file.Path

enum class ConfigMethod(val tryToGetConfig: (RequiredConfig, Path?) -> String?) {
    FileStorage(getFromFile),
    EnvironmentVariables(getFromEnvVar)
}

val getFromFile = fun (requiredConfig: RequiredConfig, configDir: Path?): String? = try {
    File(configDir.toString() + File.separator + requiredConfig.name.toLowerCase()).readText()
} catch (e: Exception) {
    null
}

val getFromEnvVar = fun (requiredConfig: RequiredConfig, _: Path?): String? = try {
    System.getenv(requiredConfig.name)
} catch (e: Exception) {
    null
}
object Configurator {

    operator fun invoke(requiredConfig: List<RequiredConfig>, configDir: Path?): Configuration {

        val foundConfig = requiredConfig.map { required ->
            fun lookForConfig(tried: List<ConfigMethod> = emptyList()): Pair<RequiredConfig, String?> {
                val methodToTry: ConfigMethod? = ConfigMethod.values().toList().find { tried.contains(it).not() }

                return when {
                    methodToTry != null -> {
                        val config = methodToTry.tryToGetConfig(required, configDir)
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

        return when {
            foundConfig.containsValue(null) -> {
                val missingConfig = foundConfig.filter { it.value == null }.map { it.key }
                throw ConfigurationException(
                        "Config value${pluralise(missingConfig)} required for " +
                                "${missingConfig.joinToString(", ") { it.name }} " +
                                "but not found."
                )
            }
            else -> Configuration(foundConfig.filterNotNull(), configDir)
        }
    }
    private fun pluralise(missingConfig: List<RequiredConfig>): String = when {
        missingConfig.size > 1 -> "s"
        else                   -> ""
    }

}

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterNotNull() = this.filterValues { it != null } as Map<K, V>
