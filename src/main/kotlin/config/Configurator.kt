package config

import java.io.File
import java.nio.file.Path

enum class ConfigMethod(val tryToGetConfig: (RequiredConfigItem, Path?) -> String?) {
    FileStorage(getFromFile),
    EnvironmentVariables(getFromEnvVar)
}

val getFromFile = fun (requiredConfig: RequiredConfigItem, configDir: Path?): String? = try {
    File(configDir.toString() + File.separator + requiredConfig.name.toLowerCase()).readText()
} catch (e: Exception) {
    null
}

val getFromEnvVar = fun (requiredConfig: RequiredConfigItem, _: Path?): String? = try {
    System.getenv(requiredConfig.name)
} catch (e: Exception) {
    null
}

interface RequiredConfigItem: Comparable<RequiredConfigItem> {
    override fun compareTo(other: RequiredConfigItem) = when {
        this.name > other.name  -> 1
        this.name == other.name -> 0
        else                    -> -1
    }
    val name: String get() = this.javaClass.simpleName
}

fun <T : RequiredConfigItem> MutableMap<T, String>.removeAndSet(configItem: T, value: String) {
    remove(this.filter { it.key.name == configItem.name }.keys.first())
    set(configItem, value)
}

abstract class RequiredConfig(private val jobName: String) {
    val formattedJobName: FormattedJobName = formatJobName()
    abstract fun values(): Set<RequiredConfigItem>

    private fun formatJobName() = FormattedJobName(jobName.toUpperCase().replace(' ', '_'))
}

data class FormattedJobName(val value: String)

object Configurator {

    operator fun invoke(requiredConfig: RequiredConfig, configDir: Path?): Configuration {

        val foundConfig = requiredConfig.values().map { required ->
            fun lookForConfig(tried: List<ConfigMethod> = emptyList()): Pair<RequiredConfigItem, String?> {
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

        return Configuration(foundConfig, requiredConfig, configDir)
    }
}
