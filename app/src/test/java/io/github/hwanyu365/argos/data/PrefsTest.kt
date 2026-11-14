package io.github.hwanyu365.argos.data

import android.content.SharedPreferences
import io.github.hwanyu365.argos.child.Detail
import io.github.hwanyu365.argos.child.OpenSession
import io.github.hwanyu365.argos.child.Pip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrefsTest {
    @Test
    fun `TC#62 처리 커서 세 값은 한 번의 커밋으로 기록된다`() {
        val sp = FakePrefs()
        val open = OpenSession("yt", 10L, Detail("제목", "https://a"))
        val pip = Pip(OpenSession("maps", 5L), "cls", 7L)

        Prefs(sp).saveCursor(42L, open, pip)

        assertEquals(1, sp.commits)
        val read = Prefs(sp)
        assertEquals(42L, read.lastEventTs)
        assertEquals(open, read.openSession)
        assertEquals(pip, read.pip)
    }

    @Test
    fun `TC#62 진행 중 세션과 PiP 가 없으면 지운 값으로 함께 기록된다`() {
        val sp = FakePrefs()
        Prefs(sp).saveCursor(1L, OpenSession("yt", 1L), Pip(OpenSession("maps", 1L), null, 0L))

        Prefs(sp).saveCursor(2L, null, null)

        assertEquals(2, sp.commits)
        val read = Prefs(sp)
        assertEquals(2L, read.lastEventTs)
        assertNull(read.openSession)
        assertNull(read.pip)
    }
}

private class FakePrefs : SharedPreferences {
    val values = mutableMapOf<String, Any?>()
    var commits = 0

    override fun getAll(): Map<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val pending = mutableMapOf<String, Any?>()
        var clear = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clear) values.clear()
            pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            commits++
        }
    }
}
