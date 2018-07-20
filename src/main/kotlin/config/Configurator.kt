package config

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

interface RequiredConfig: Comparable<RequiredConfig> {
    override fun compareTo(other: RequiredConfig) = when {
        this.name > other.name  -> 1
        this.name == other.name -> 0
        else                    -> -1
    }
    val name: String get() = this.javaClass.simpleName
}

interface RequiredConfigList {
    fun values(): Set<RequiredConfig>
}

object Configurator {

    operator fun invoke(requiredConfigList: RequiredConfigList, configDir: Path?): Configuration {

        val foundConfig = requiredConfigList.values().map { required ->
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

        return Configuration(foundConfig, requiredConfigList, configDir)
    }
}
