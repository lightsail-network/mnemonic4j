package network.lightsail


/**
 * Represents the supported languages for the mnemonic words.
 *
 *
 * Each language is identified by a unique code.
 */
enum class Language(val code: String) {
    /**
     * Chinese Simplified language.
     */
    CHINESE_SIMPLIFIED("chinese_simplified"),

    /**
     * Chinese Traditional language.
     */
    CHINESE_TRADITIONAL("chinese_traditional"),

    /**
     * Czech language.
     */
    CZECH("czech"),

    /**
     * English language.
     */
    ENGLISH("english"),

    /**
     * French language.
     */
    FRENCH("french"),

    /**
     * Italian language.
     */
    ITALIAN("italian"),

    /**
     * Japanese language.
     */
    JAPANESE("japanese"),

    /**
     * Korean language.
     */
    KOREAN("korean"),

    /**
     * Portuguese language.
     */
    PORTUGUESE("portuguese"),

    /**
     * Russian language.
     */
    RUSSIAN("russian"),

    /**
     * Spanish language.
     */
    SPANISH("spanish"),

    /**
     * Turkish language.
     */
    TURKISH("turkish");

    companion object {
        val allLanguages: List<Language>
            get() = listOf(*entries.toTypedArray())
    }
}