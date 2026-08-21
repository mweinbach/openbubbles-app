package app.openbubbles.nativeapp.ui.passwords

import java.security.SecureRandom
import java.util.Random

/**
 * Suggested passwords for the create form.
 *
 * Asking someone to invent a password inside a password manager is the one
 * moment the manager should do the work. The generated value never leaves the
 * form until the user saves it, and the default source of randomness is
 * [SecureRandom]; the [random] parameter exists so the shape of the output can be
 * proven in a test, not so callers can weaken it.
 */
object VaultPasswordGenerator {
    /** Length Apple's own suggestions use, and long enough to be worth the space. */
    const val DEFAULT_LENGTH: Int = 20

    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "-_.!@#%*+=?"

    /** Ambiguous glyphs (l, 1, I, O, 0) are excluded so a read-aloud password works. */
    private val ALL = LOWER + UPPER + DIGITS + SYMBOLS

    fun generate(length: Int = DEFAULT_LENGTH, random: Random = SecureRandom()): String {
        val target = length.coerceAtLeast(MINIMUM_LENGTH)
        // One character from each class first, so a generated password always
        // satisfies the "needs a number and a symbol" rules sites impose.
        val required = listOf(LOWER, UPPER, DIGITS, SYMBOLS).map { pool ->
            pool[random.nextInt(pool.length)]
        }
        val rest = (0 until target - required.size).map { ALL[random.nextInt(ALL.length)] }
        return (required + rest).shuffled(random).joinToString("")
    }

    /** Below this a generated password is not worth suggesting. */
    const val MINIMUM_LENGTH: Int = 12

    /** True when [value] contains one of every class the generator guarantees. */
    fun isStrong(value: String): Boolean = value.length >= MINIMUM_LENGTH &&
        value.any { it.isLowerCase() } &&
        value.any { it.isUpperCase() } &&
        value.any { it.isDigit() } &&
        value.any { !it.isLetterOrDigit() }
}
