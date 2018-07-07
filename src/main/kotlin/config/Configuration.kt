package config

import GmailBot.Companion.RequiredConfig
import java.nio.file.Path

class Configuration(private val values: Map<RequiredConfig, String>, val configDir: Path?) {
    fun fetch(requiredConfig: RequiredConfig): String = try {
        this.values[requiredConfig]!!
    } catch (e: Exception) {
        throw ConfigurationException("$requiredConfig was not available during fetch")
    }
}

class ConfigurationException(override val message: String) : RuntimeException()