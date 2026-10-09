package io.github.hwanyu365.argos.data

import java.security.SecureRandom

// spec D#4: 수동 입력이 가능한 길이이면서 10분 안에 무작위 대입이 불가능한 50bit.
object PairingCode {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val LENGTH = 10
    const val TTL_MS = 10 * 60_000L

    fun generate(random: SecureRandom = SecureRandom()): String = buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    fun expiresAt(nowMs: Long): Long = nowMs + TTL_MS

    // Crockford 규칙대로 사람이 헷갈리는 문자를 받아준다.
    fun normalize(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }.map {
        when (it) {
            'O' -> '0'
            'I', 'L' -> '1'
            else -> it
        }
    }.joinToString("")
}
