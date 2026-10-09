package io.github.hwanyu365.argos.data

import android.content.Context
import androidx.core.content.edit
import io.github.hwanyu365.argos.child.Detail
import io.github.hwanyu365.argos.child.OpenSession
import io.github.hwanyu365.argos.child.Pip

enum class Role {
    PARENT,
    CHILD
    ;

    companion object
}

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

    /** FR#1 초기화: 역할·가족·수집 커서를 모두 지운다. */
    fun clear() = sp.edit { clear() }

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

    /** 재시작 후에도 PiP 를 이어서 닫을 수 있게 저장한다 (S#8, S#9). */
    var pip: Pip?
        get() = sp.getString("pip.pkg", null)?.let { Pip(OpenSession(it, sp.getLong("pip.start", 0)), sp.getString("pip.cls", null), sp.getLong("pip.handoff", 0)) }
        set(v) = sp.edit {
            putString("pip.pkg", v?.open?.pkg)
            putLong("pip.start", v?.open?.start ?: 0)
            putString("pip.cls", v?.cls)
            putLong("pip.handoff", v?.handoff ?: 0)
        }
}
