package config

import GmailBot.Companion.RequiredConfig

typealias Configuration = Map<RequiredConfig, String>

class ConfigurationException(override val message: String) : RuntimeException()

fun Configuration.fetch(requiredConfig: RequiredConfig): String = try {
    this[requiredConfig]!!
} catch (e: Exception) {
    throw ConfigurationException("$requiredConfig was not available during fetch")
}