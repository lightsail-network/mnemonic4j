import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import network.lightsail.Language
import network.lightsail.Mnemonic
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random

class MnemonicTest {
    @Test
    fun testVectors() {
        val inputStream = MnemonicTest::class.java.classLoader.getResourceAsStream("vectors.json")
        assertNotNull(inputStream, "Resource vectors.json should not be null")
        val type = object : TypeToken<Map<String, List<List<String>>>>() {}.type
        val vectors: Map<String, List<List<String>>> = Gson().fromJson(inputStream!!.reader(), type)
        for ((lang, vectorList) in vectors) {
            checkList(lang, vectorList)
        }
    }

    private fun checkList(language: String, vectors: List<List<String>>) {
        val m = Mnemonic(Language.valueOf(language.uppercase()))
        for (v in vectors) {
            val code = m.toMnemonic(v[0].hexStringToByteArray())
            val seed = Mnemonic.toSeed(code, passphrase = "TREZOR")
            val xprv = Mnemonic.toHdMasterKey(seed)
            val msg = "Failed case: $code"
            assertTrue(m.check(v[1]), msg)
            assertEquals(v[1], code, msg)
            assertEquals(v[2], seed.toHexString(), msg)
            assertEquals(v[3], xprv, msg)
        }
    }

    @Test
    fun testFailedChecksum() {
        val code = "bless cloud wheel regular tiny venue bird web grief security dignity zoo"
        val m = Mnemonic(Language.ENGLISH)
        assertFalse(m.check(code))
    }

    @Test
    fun testDetection() {
        assertEquals(Language.ENGLISH, Mnemonic.detectLanguage("security"))
        assertEquals(Language.ENGLISH, Mnemonic.detectLanguage("fruit wave dwarf"))
        assertEquals(Language.ENGLISH, Mnemonic.detectLanguage("fru wago dw"))
        assertEquals(Language.FRENCH, Mnemonic.detectLanguage("fru wago dur enje"))

        assertThrows<IllegalArgumentException> { Mnemonic.detectLanguage("jaguar xxxxxxx") }
        assertThrows<IllegalArgumentException> { Mnemonic.detectLanguage("jaguar jaguar") }

        assertEquals(Language.ENGLISH, Mnemonic.detectLanguage("jaguar security"))
        assertEquals(Language.FRENCH, Mnemonic.detectLanguage("jaguar aboyer"))
        assertEquals(Language.ENGLISH, Mnemonic.detectLanguage("abandon about"))
        assertEquals(Language.FRENCH, Mnemonic.detectLanguage("abandon aboutir"))
        assertEquals(Language.FRENCH, Mnemonic.detectLanguage("fav financer"))
        assertEquals(Language.CZECH, Mnemonic.detectLanguage("fav finance"))
        assertThrows<IllegalArgumentException> { Mnemonic.detectLanguage("favor finan") }
        assertEquals(Language.CZECH, Mnemonic.detectLanguage("flanel"))
        assertEquals(Language.PORTUGUESE, Mnemonic.detectLanguage("flanela"))
        assertThrows<IllegalArgumentException> { Mnemonic.detectLanguage("flane") }
    }

    @Test
    fun testUtf8Nfkd() {
        val wordsNfkd =
            "Příšerně žluťoučký kůň úpěl ďábelské ódy zákeřný učeň běží podél zóny úlů"
        val wordsNfc = "Příšerně žluťoučký kůň úpěl ďábelské ódy zákeřný učeň běží podél zóny úlů"
        val wordsNfkc = "Příšerně žluťoučký kůň úpěl ďábelské ódy zákeřný učeň běží podél zóny úlů"
        val wordsNfd =
            "Příšerně žluťoučký kůň úpěl ďábelské ódy zákeřný učeň běží podél zóny úlů"

        val passphraseNfkd = "Neuvěřitelně bezpečné heslíčko"
        val passphraseNfc = "Neuvěřitelně bezpečné heslíčko"
        val passphraseNfkc = "Neuvěřitelně bezpečné heslíčko"
        val passphraseNfd = "Neuvěřitelně bezpečné heslíčko"

        val seedNfkd = Mnemonic.toSeed(wordsNfkd, passphraseNfkd)
        val seedNfc = Mnemonic.toSeed(wordsNfc, passphraseNfc)
        val seedNfkc = Mnemonic.toSeed(wordsNfkc, passphraseNfkc)
        val seedNfd = Mnemonic.toSeed(wordsNfd, passphraseNfd)

        assertArrayEquals(seedNfkd, seedNfc)
        assertArrayEquals(seedNfkd, seedNfkc)
        assertArrayEquals(seedNfkd, seedNfd)
    }

    @Test
    fun testToEntropy() {
        val data = List(1024) { Random.nextBytes(32) } + listOf("Lorem ipsum dolor sit amet amet.".toByteArray())
        val m = Mnemonic(Language.ENGLISH)
        for (d in data) {
            assertEquals(d.toList(), m.toEntropy(m.toMnemonic(d).split(" ")).toList())
        }
    }

    @Test
    fun testExpandWord() {
        val m = Mnemonic(Language.ENGLISH)
        assertEquals("", m.expandWord(""))
        assertEquals(" ", m.expandWord(" "))
        assertEquals("access", m.expandWord("access"))
        assertEquals("access", m.expandWord("acce"))
        assertEquals("acb", m.expandWord("acb"))
        assertEquals("acc", m.expandWord("acc"))
        assertEquals("act", m.expandWord("act"))
        assertEquals("action", m.expandWord("acti"))
    }

    @Test
    fun testExpand() {
        val m = Mnemonic(Language.ENGLISH)
        assertEquals("access", m.expand("access"))
        assertEquals("access access acb acc act action", m.expand("access acce acb acc act acti"))
    }

    @Test
    fun testCustomWordlist() {
        val wordlist = ArrayList<String>()
        for (i in 1 until 2049) {
            wordlist.add("word$i")
        }
        val m = Mnemonic(Language.ENGLISH, wordlist)
        val mnemonic = m.toMnemonic("80808080808080808080808080808080".hexStringToByteArray())
        assertEquals(
            "word1029 word33 word258 word9 word65 word515 word17 word129 word1029 word33 word258 word5",
            mnemonic
        )
    }

    @Test
    fun testCustomWordlistWithInvalidSize() {
        val wordlist = ArrayList<String>()
        for (i in 1 until 2048) {
            wordlist.add("word$i")
        }
        assertThrows<IllegalArgumentException> { Mnemonic(Language.ENGLISH, wordlist) }
    }

    @Test
    fun testGenerateWithInvalidSize() {
        val m = Mnemonic(Language.ENGLISH)
        assertThrows<IllegalArgumentException> { m.generate(129) }
    }

    @Test
    fun testToEntropyWithInvalidSize() {
        val m = Mnemonic(Language.ENGLISH)
        val words = "abandon about abandon about abandon about abandon about".split(" ")
        assertThrows<IllegalArgumentException> { m.toEntropy(words) }
    }

    @Test
    fun testToMnemonicWithInvalidSize() {
        val m = Mnemonic(Language.ENGLISH)
        val entropy = "808080808080808080808080808080808080808080808080808080808080808080".hexStringToByteArray()
        assertThrows<IllegalArgumentException> { m.toMnemonic(entropy) }
    }

    @ParameterizedTest
    @MethodSource("testGenerateProvider")
    fun testGenerate(language: Language, strength: Int, expectedWordCount: Int) {
        val m = Mnemonic(language)
        val mnemonic = m.generate(strength)
        println(mnemonic)
        assertTrue(m.check(mnemonic))
        assertEquals(expectedWordCount, mnemonic.split(if (language == Language.JAPANESE) "\u3000" else " ").size)
    }

    private fun String.hexStringToByteArray(): ByteArray {
        val len = this.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((this[i].digitToInt(16) shl 4) + this[i + 1].digitToInt(16)).toByte()
        }
        return data
    }

    private fun ByteArray.toHexString(): String {
        return this.joinToString("") { "%02x".format(it) }
    }

    companion object {
        @JvmStatic
        fun testGenerateProvider() = listOf(
            Arguments.of(Language.ENGLISH, 128, 12),
            Arguments.of(Language.ENGLISH, 160, 15),
            Arguments.of(Language.ENGLISH, 192, 18),
            Arguments.of(Language.ENGLISH, 224, 21),
            Arguments.of(Language.ENGLISH, 256, 24),
            Arguments.of(Language.CHINESE_SIMPLIFIED, 128, 12),
            Arguments.of(Language.CHINESE_SIMPLIFIED, 160, 15),
            Arguments.of(Language.CHINESE_SIMPLIFIED, 192, 18),
            Arguments.of(Language.CHINESE_SIMPLIFIED, 224, 21),
            Arguments.of(Language.CHINESE_SIMPLIFIED, 256, 24),
            Arguments.of(Language.CHINESE_TRADITIONAL, 128, 12),
            Arguments.of(Language.CZECH, 128, 12),
            Arguments.of(Language.FRENCH, 128, 12),
            Arguments.of(Language.ITALIAN, 128, 12),
            Arguments.of(Language.JAPANESE, 128, 12),
            Arguments.of(Language.KOREAN, 128, 12),
            Arguments.of(Language.PORTUGUESE, 128, 12),
            Arguments.of(Language.RUSSIAN, 128, 12),
            Arguments.of(Language.SPANISH, 128, 12),
            Arguments.of(Language.TURKISH, 128, 12),
        )
    }
}