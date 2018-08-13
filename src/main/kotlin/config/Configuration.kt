package config

import java.nio.file.Path
import java.time.DayOfWeek

data class Configuration(private val config: Map<RequiredConfigItem, String?>, private val requiredConfig: RequiredConfig, val configDir: Path?) {

    init {
        validate(requiredConfig.values().toSet(), config)
    }

    fun get(item: RequiredConfigItem): String = try {
        this.config[item]!!
    } catch (e: Exception) {
        throw ConfigurationException("$item was not available during get")
    }

    inline fun <reified T> getAsListOf(item: RequiredConfigItem, transform: (String) -> T, delimiter: Char = ','): List<T> = try {
        this.get(item).split(delimiter).map { it.trim() }.map { transform(it) }
    } catch (e: Exception) {
        throw ConfigurationException("It was not safe to return ${item.name} (value: ${this.get(item)}) as a list of ${T::class}")
    }

    private fun validate(required: Set<RequiredConfigItem>, provided: Map<RequiredConfigItem, String?>) {
        fun pluralise(missingConfig: List<RequiredConfigItem>): String = when {
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
val stringToDayOfWeek = { s: String -> DayOfWeek.valueOf(s.toUpperCase()) }
val stringToInt = { s: String -> s.toInt() }
