package config

import java.nio.file.Path

data class Configuration(private val config: Map<RequiredConfig, String?>, private val configList: RequiredConfigList, val configDir: Path?) {

    init {
        validate(configList.values().toSet(), config)
    }

    fun get(value: RequiredConfig): String = try {
        this.config[value]!!
    } catch (e: Exception) {
        throw ConfigurationException("$value was not available during get")
    }

    fun getAsListOfInt(value: RequiredConfig, delimiter: Char = ','): List<Int> = try {
        this.get(value).split(delimiter).map { it.trim().toInt() }
    } catch (e: Exception) {
        throw ConfigurationException("It was not safe to return $value as a list of ${Int::class}")
    }

    private fun validate(required: Set<RequiredConfig>, provided: Map<RequiredConfig, String?>) {
        fun pluralise(missingConfig: List<RequiredConfig>): String = when {
            missingConfig.size > 1 -> "s"
            else                   -> ""
        }

        if (required.sorted() != provided.keys.sorted() || provided.values.contains(null)) {
            val completelyMissing  = required.filter { provided.contains(it).not() }
            val nullValues = provided.filter { it.value == null }.keys
            val missingConfig = completelyMissing + nullValues
            throw ConfigurationException(
                    "Config value${pluralise(missingConfig)} required but not found:".newlines(2) +
                    missingConfig.joinToString(osNewline) { it.name }.newlines(1)
            )
        }
    }
}

val osNewline: String = System.getProperty("line.separator")
fun String.newlines(numberOf: Int) = this + (1..numberOf).map { osNewline }.joinToString("")
class ConfigurationException(override val message: String) : RuntimeException()
