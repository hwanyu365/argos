package io.github.hwanyu365.argos.child

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// ponytail: 테이블 하나라 Room 대신 플랫폼 SQLite 를 쓴다. 테이블·질의가 늘면 Room 으로 옮긴다.
class SessionStore(context: Context) : SQLiteOpenHelper(context, NAME, null, 1) {
    companion object {
        const val NAME = "sessions.db"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE session (id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, start INTEGER NOT NULL, `end` INTEGER NOT NULL, title TEXT, url TEXT, uploaded INTEGER NOT NULL DEFAULT 0, UNIQUE(pkg, start))")
        db.execSQL("CREATE INDEX session_start ON session(start)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** [from] 이후에 끝난 세션. 일별 집계는 이 구간을 자정 기준으로 다시 나눈다. */
    fun endingAfter(from: Long): List<Session> = readableDatabase.rawQuery(
        "SELECT pkg, start, `end`, title, url FROM session WHERE `end` > ? ORDER BY start",
        arrayOf(from.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(Session(c.getString(0), c.getLong(1), c.getLong(2), c.getString(3), c.getString(4))) } }

    fun earliestStart(): Long? = readableDatabase.rawQuery("SELECT MIN(start) FROM session", null).use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
    }

    /** FR#14: 보관 기간이 지난 세션을 지운다. */
    fun deleteEndedBefore(cutoff: Long) {
        writableDatabase.delete("session", "`end` < ?", arrayOf(cutoff.toString()))
    }

    fun insert(sessions: List<Session>) {
        if (sessions.isEmpty()) return
        writableDatabase.run {
            beginTransaction()
            try {
                sessions.forEach {
                    // 커서 저장 전에 죽어 같은 이벤트를 다시 처리해도 세션이 두 번 쌓이지 않게 한다 (S#8).
                    insertWithOnConflict(
                        "session",
                        null,
                        ContentValues().apply {
                            put("pkg", it.pkg)
                            put("start", it.start)
                            put("end", it.end)
                            put("title", it.title)
                            put("url", it.url)
                        },
                        SQLiteDatabase.CONFLICT_IGNORE
                    )
                }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }
}
