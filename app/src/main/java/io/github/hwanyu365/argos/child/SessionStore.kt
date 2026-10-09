package io.github.hwanyu365.argos.child

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// ponytail: 테이블 하나라 Room 대신 플랫폼 SQLite 를 쓴다. 테이블·질의가 늘면 Room 으로 옮긴다.
class SessionStore(context: Context) : SQLiteOpenHelper(context, "sessions.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE session (id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, start INTEGER NOT NULL, `end` INTEGER NOT NULL, title TEXT, url TEXT, uploaded INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX session_start ON session(start)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(sessions: List<Session>) {
        if (sessions.isEmpty()) return
        writableDatabase.run {
            beginTransaction()
            try {
                sessions.forEach {
                    insert(
                        "session",
                        null,
                        ContentValues().apply {
                            put("pkg", it.pkg)
                            put("start", it.start)
                            put("end", it.end)
                            put("title", it.title)
                            put("url", it.url)
                        }
                    )
                }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }
}
