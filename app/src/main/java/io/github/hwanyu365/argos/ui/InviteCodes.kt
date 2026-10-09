package io.github.hwanyu365.argos.ui

import io.github.hwanyu365.argos.data.PairingCode

/**
 * 초대 다이얼로그가 발급한 코드의 수명 관리 (FR#4). 화면에 보이는 코드 외에는 모두 지울 대상이다.
 * 발급은 비동기라, 늦게 끝난 이전 발급분이나 닫은 뒤 끝난 발급분도 지울 대상으로 돌린다.
 */
class InviteCodes {
    private var latest = 0
    private var current: String? = null
    private var closed = false
    private val obsolete = mutableListOf<String>()

    /** 새 발급을 시작한다. 지금 보이는 코드는 지울 대상이 된다. */
    fun begin(): Int {
        current?.let { obsolete += it }
        current = null
        return ++latest
    }

    /** 발급이 끝났다. 가장 최근 발급이고 아직 열려 있을 때만 화면에 보인다(true). */
    fun complete(token: Int, code: String): Boolean {
        if (closed || token != latest) {
            obsolete += code
            return false
        }
        current = code
        return true
    }

    fun takeObsolete(): List<String> = obsolete.toList().also { obsolete.clear() }

    /** 닫는다. 남은 코드를 모두 돌려준다. 이후에 끝나는 발급분은 complete 가 지울 대상으로 돌린다. */
    fun close(): List<String> {
        closed = true
        return (obsolete + listOfNotNull(current)).also {
            obsolete.clear()
            current = null
        }
    }

    companion object {
        fun fromScan(raw: String) = PairingCode.normalize(raw).take(10)
    }
}
