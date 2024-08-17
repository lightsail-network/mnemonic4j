package network.lightsail

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.stream.Collectors
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val PBKDF2_ROUNDS = 2048
private const val WORDLIST_RADIX = 2048

/**
 * Provides utility methods to generate random mnemonics and also generate seeds from mnemonics.
 *
 * @property language The language of the mnemonic words, defaults to [Language.ENGLISH].
 * @property wordlist The list of words for the given language, or null if the default wordlist should be used.
 * @see [Mnemonic code for generating deterministic keys](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki)
 */
class Mnemonic(val language: Language = Language.ENGLISH, wordlist: List<String>? = null) {
    private val wordlist: List<String>
    private val delimiter: String

    init {
        val wordlistFile = wordlist ?: run {
            val resourceName = "wordlist/${language.code}.txt"
            val inputStream = Mnemonic::class.java.classLoader.getResourceAsStream(resourceName)
            checkNotNull(inputStream) { "Word list file not found for language: $language" }
            inputStream.bufferedReader().useLines { it.toList() }
        }

        require(wordlistFile.size == WORDLIST_RADIX) { "Wordlist must contain $WORDLIST_RADIX words." }

        this.wordlist = wordlistFile
        this.delimiter = if (language == Language.JAPANESE) "\u3000" else " "
    }

    companion object {

        /**
         * Detects the language of the mnemonic words.
         *
         * @param mnemonic The mnemonic words.
         * @return The language code of the mnemonic words.
         * @throws IllegalArgumentException If the language cannot be detected.
         */
        @JvmStatic
        fun detectLanguage(mnemonic: String): Language {
            val normalizedMnemonic = normalizeString(mnemonic)
            val possible: MutableSet<Mnemonic> = HashSet()
            for (lang in Language.allLanguages) {
                possible.add(Mnemonic(lang))
            }

            val words: Set<String> =
                HashSet(listOf(*normalizedMnemonic.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()))
            for (word in words) {
                possible.removeIf { p: Mnemonic -> p.wordlist.stream().noneMatch { c: String -> c.startsWith(word) } }
                require(possible.isNotEmpty()) { "Language unrecognized for word: $word" }
            }

            if (possible.size == 1) {
                return possible.iterator().next().language
            }

            val complete: MutableSet<Mnemonic> = HashSet()
            for (word in words) {
                val exact = possible.stream()
                    .filter { p: Mnemonic -> p.wordlist.contains(word) }
                    .collect(Collectors.toSet())
                if (exact.size == 1) {
                    complete.addAll(exact)
                }
            }

            if (complete.size == 1) {
                return complete.iterator().next().language
            }

            throw IllegalArgumentException(
                "Language ambiguous between " +
                        possible.stream().map { p: Mnemonic -> p.language.code }
                            .collect(Collectors.joining(", "))
            )
        }

        /**
         * Converts the mnemonic words to a seed.
         *
         * @param mnemonic The mnemonic words.
         * @param passphrase The passphrase to use for the seed, defaults to an empty string.
         * @return The seed.
         */
        @JvmStatic
        @JvmOverloads
        fun toSeed(mnemonic: String, passphrase: String = ""): ByteArray {
            val normalizedMnemonic = normalizeString(mnemonic)
            val normalizedPassphrase = normalizeString("mnemonic$passphrase")
            val mnemonicBytes = normalizedMnemonic.toByteArray(StandardCharsets.UTF_8)
            val passphraseBytes = normalizedPassphrase.toByteArray(StandardCharsets.UTF_8)

            val spec = PBEKeySpec(
                String(mnemonicBytes, Charsets.UTF_8).toCharArray(),
                passphraseBytes,
                PBKDF2_ROUNDS,
                512
            )
            val skf = getSecretKeyFactory("PBKDF2WithHmacSHA512")
            return skf.generateSecret(spec).encoded.copyOfRange(0, 64)
        }

        /**
         * Converts the seed to a BIP32 HD master key.
         *
         * @param seed The seed.
         * @param testnet Whether to generate a testnet key, defaults to false.
         * @return The HD master key.
         * @throws IllegalArgumentException If the seed length is not 64 bytes.
         */
        @JvmStatic
        @JvmOverloads
        fun toHdMasterKey(seed: ByteArray, testnet: Boolean = false): String {
            require(seed.size == 64) { "Provided seed should have length of 64" }

            val hmacKey = "Bitcoin seed".toByteArray(StandardCharsets.UTF_8)
            val mac = getMac("HmacSHA512")
            mac.init(SecretKeySpec(hmacKey, "HmacSHA512"))
            val seedHmac = mac.doFinal(seed)

            val prefix = if (testnet) {
                byteArrayOf(0x04.toByte(), 0x35.toByte(), 0x83.toByte(), 0x94.toByte())
            } else {
                byteArrayOf(0x04.toByte(), 0x88.toByte(), 0xad.toByte(), 0xe4.toByte())
            }
            val xprv =
                prefix + ByteArray(9) + seedHmac.copyOfRange(32, 64) + byteArrayOf(0x00) + seedHmac.copyOfRange(0, 32)

            val hashedXprv = MessageDigest.getInstance("SHA-256").digest(xprv)
            val doubleHashedXprv = MessageDigest.getInstance("SHA-256").digest(hashedXprv)
            val xprvWithChecksum = xprv + doubleHashedXprv.copyOfRange(0, 4)

            return base58Encode(xprvWithChecksum)
        }

        /**
         * Normalizes the given string by converting it to UTF-8 and applying NFKD normalization.
         *
         * @param txt The input string or byte array.
         * @return The normalized string.
         * @throws IllegalArgumentException If the input is not a string or byte array.
         */
        private fun normalizeString(txt: Any): String {
            val utxt = when (txt) {
                is ByteArray -> txt.toString(Charsets.UTF_8)
                is String -> txt
                else -> throw IllegalArgumentException("String value expected")
            }
            return Normalizer.normalize(utxt, Normalizer.Form.NFKD)
        }
    }

    /**
     * Generates a random mnemonic.
     *
     * @param strength The strength of the mnemonic, defaults to 128 bits. Allowed values are [128, 160, 192, 224, 256].
     * @return The generated mnemonic.
     * @throws IllegalArgumentException If the strength is not one of the allowed values.
     */
    @JvmOverloads
    fun generate(strength: Int = 128): String {
        require(strength in listOf(128, 160, 192, 224, 256)) {
            "Invalid strength value. Allowed values are [128, 160, 192, 224, 256]."
        }
        val entropy = ByteArray(strength / 8)
        SecureRandom().nextBytes(entropy)
        return toMnemonic(entropy)
    }

    /**
     * Converts the mnemonic words to entropy.
     *
     * @param words The mnemonic words.
     * @return The entropy.
     * @throws IllegalArgumentException If the number of words is not one of [12, 15, 18, 21, 24].
     * @throws IllegalArgumentException If the checksum fails.
     * @throws IllegalArgumentException If a word is not found in the wordlist.
     */
    fun toEntropy(words: List<String>): ByteArray {
        require(words.size in listOf(12, 15, 18, 21, 24)) {
            "Number of words must be one of the following: [12, 15, 18, 21, 24], but it is not (${words.size})."
        }

        val concatLenBits = words.size * 11
        val concatBits = BooleanArray(concatLenBits)

        for ((wordIndex, word) in words.withIndex()) {
            val ndx = wordlist.indexOf(normalizeString(word))
            require(ndx >= 0) { "Unable to find \"$word\" in word list." }

            for (ii in 0 until 11) {
                concatBits[wordIndex * 11 + ii] = (ndx and (1 shl (10 - ii))) != 0
            }
        }

        val checksumLengthBits = concatLenBits / 33
        val entropyLengthBits = concatLenBits - checksumLengthBits
        val entropy = ByteArray(entropyLengthBits / 8)

        for (ii in entropy.indices) {
            for (jj in 0 until 8) {
                if (concatBits[ii * 8 + jj]) {
                    entropy[ii] = (entropy[ii].toInt() or (1 shl (7 - jj))).toByte()
                }
            }
        }

        val hashBytes = MessageDigest.getInstance("SHA-256").digest(entropy)
        val hashBits = hashBytes.flatMap { byte ->
            (0 until 8).map { bit -> (byte.toInt() and (1 shl (7 - bit))) != 0 }
        }

        repeat(checksumLengthBits) { i ->
            require(concatBits[entropyLengthBits + i] == hashBits[i]) { "Failed checksum." }
        }

        return entropy
    }

    /**
     * Converts the entropy to mnemonic words.
     *
     * @param entropy The entropy. The length should be one of [16, 20, 24, 28, 32].
     * @return The mnemonic words.
     * @throws IllegalArgumentException If the entropy length is not one of [16, 20, 24, 28, 32].
     * @throws IllegalArgumentException If the checksum fails.
     */
    fun toMnemonic(entropy: ByteArray): String {
        require(entropy.size in listOf(16, 20, 24, 28, 32)) {
            "Data length should be one of the following: [16, 20, 24, 28, 32], but it is not ${entropy.size}."
        }

        val hash = getMessageDigest("SHA-256").digest(entropy)
        val b = entropy.toBitString() + hash.toBitString().substring(0, entropy.size * 8 / 32)
        val result = mutableListOf<String>()

        for (i in 0 until b.length / 11) {
            val idx = b.substring(i * 11, (i + 1) * 11).toInt(2)
            result.add(wordlist[idx])
        }

        return result.joinToString(delimiter)
    }

    /**
     * Checks if the mnemonic is valid.
     *
     * @param mnemonic The mnemonic words.
     * @return True if the mnemonic is valid, false otherwise.
     */
    fun check(mnemonic: String): Boolean {
        val mnemonicList = normalizeString(mnemonic).split(" ")
        if (mnemonicList.size !in listOf(12, 15, 18, 21, 24)) {
            return false
        }

        return try {
            val idx = mnemonicList.map { wordlist.indexOf(it).toString(2).padStart(11, '0') }
            val b = idx.joinToString("")
            val l = b.length
            val d = b.substring(0, l / 33 * 32)
            val h = b.substring(l / 33 * 32)
            val nd = d.toBigInteger(2).toBytes(l / 33 * 4)
            val nh = MessageDigest.getInstance("SHA-256").digest(nd).toBitString().substring(0, l / 33)
            h == nh
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Expands the given word prefix to a complete word from the wordlist.
     *
     * @param prefix The word prefix.
     * @return The expanded word if a unique match is found, otherwise the original prefix.
     */
    fun expandWord(prefix: String): String {
        return if (prefix in wordlist) {
            prefix
        } else {
            val matches = wordlist.filter { it.startsWith(prefix) }
            if (matches.size == 1) {
                matches[0]
            } else {
                prefix
            }
        }
    }

    /**
     * Expands the given mnemonic by expanding each word prefix to a complete word from the wordlist.
     *
     * @param mnemonic The mnemonic words.
     * @return The expanded mnemonic.
     */
    fun expand(mnemonic: String): String {
        return mnemonic.split(" ").joinToString(" ") { expandWord(it) }
    }
}

/**
 * Converts the BigInteger to a byte array of the specified length.
 *
 * @param length The desired length of the byte array.
 * @return The byte array.
 */
private fun BigInteger.toBytes(length: Int): ByteArray {
    val bytes = this.toByteArray()
    return when {
        bytes.size < length -> ByteArray(length - bytes.size) + bytes
        bytes.size > length -> bytes.drop(1).toByteArray()
        else -> bytes
    }
}

/**
 * Converts the byte array to a bit string.
 *
 * @return The bit string.
 */
private fun ByteArray.toBitString(): String {
    return this.joinToString("") { it.toInt().and(0xFF).toString(2).padStart(8, '0') }
}

/**
 * Encodes the byte array using base58 encoding.
 *
 * @param v The byte array to encode.
 * @return The base58-encoded string.
 */
private fun base58Encode(v: ByteArray): String {
    val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    val base = alphabet.length
    var num = v.fold(0.toBigInteger()) { acc, byte -> (acc shl 8) + (byte.toInt() and 0xFF).toBigInteger() }

    val encoded = StringBuilder()
    while (num > BigInteger.ZERO) {
        val remainder = (num % base.toBigInteger()).toInt()
        num /= base.toBigInteger()
        encoded.append(alphabet[remainder])
    }

    v.takeWhile { it.toInt() == 0 }.forEach { _ -> encoded.append(alphabet[0]) }

    return encoded.reverse().toString()
}

// try import BouncyCastle library
private val bouncyCastleProvider = tryImport {
    Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
        .getConstructor()
        .newInstance() as java.security.Provider
}

// Choose the implementation of MessageDigest based on whether BouncyCastle library exists
private fun getMessageDigest(algorithm: String): MessageDigest {
    return if (bouncyCastleProvider != null) {
        MessageDigest.getInstance(algorithm, bouncyCastleProvider)
    } else {
        MessageDigest.getInstance(algorithm)
    }
}

// Choose the implementation of Mac based on whether BouncyCastle library exists
private fun getMac(algorithm: String): Mac {
    return if (bouncyCastleProvider != null) {
        Mac.getInstance(algorithm, bouncyCastleProvider)
    } else {
        Mac.getInstance(algorithm)
    }
}

// Choose the implementation of SecretKeyFactory based on whether BouncyCastle library exists
private fun getSecretKeyFactory(algorithm: String): SecretKeyFactory {
    return if (bouncyCastleProvider != null) {
        SecretKeyFactory.getInstance(algorithm, bouncyCastleProvider)
    } else {
        SecretKeyFactory.getInstance(algorithm)
    }
}

// Try to import the specified class, return null if the class does not exist
inline fun <reified T> tryImport(block: () -> T): T? {
    return try {
        block()
    } catch (e: ClassNotFoundException) {
        null
    }
}
