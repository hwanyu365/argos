package io.github.hwanyu365.argos.data

import android.content.Context
import androidx.core.content.edit
import io.github.hwanyu365.argos.child.Detail
import io.github.hwanyu365.argos.child.OpenSession

enum class Role { PARENT, CHILD }

/** 기기에 남는 작은 상태. 세션 목록은 SessionStore 가 맡는다. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("argos", Context.MODE_PRIVATE)

    var role: Role?
        get() = sp.getString("role", null)?.let(Role::valueOf)
        set(v) = sp.edit { putString("role", v?.name) }

    var familyId: String?
        get() = sp.getString("familyId", null)
        set(v) = sp.edit { putString("familyId", v) }

    /** 자녀가 감시를 시작했는지. 재부팅·watchdog 은 이 값이 true 일 때만 서비스를 되살린다. */
    var monitoring: Boolean
        get() = sp.getBoolean("monitoring", false)
        set(v) = sp.edit { putBoolean("monitoring", v) }

    var lastEventTs: Long
        get() = sp.getLong("lastEventTs", 0)
        set(v) = sp.edit { putLong("lastEventTs", v) }

    var openSession: OpenSession?
        get() = sp.getString("open.pkg", null)?.let { OpenSession(it, sp.getLong("open.start", 0), Detail(sp.getString("open.title", null), sp.getString("open.url", null))) }
        set(v) = sp.edit {
            putString("open.pkg", v?.pkg)
            putLong("open.start", v?.start ?: 0)
            putString("open.title", v?.detail?.title)
            putString("open.url", v?.detail?.url)
        }
}
