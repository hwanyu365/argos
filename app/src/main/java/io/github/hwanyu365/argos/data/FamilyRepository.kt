package io.github.hwanyu365.argos.data

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import io.github.hwanyu365.argos.child.Live
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

data class Member(val uid: String, val role: Role, val name: String)

data class ChildLive(val live: Live?, val updatedAt: Long?, val usageGranted: Boolean = true)

data class FamilySnapshot(val members: List<Member>, val live: Map<String, ChildLive>, val apps: Map<String, String>)

class JoinException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_FOUND, EXPIRED }
}

/** 보안 규칙(§4.3)이 허용하는 쓰기만 한다. 실패는 규칙 거부·네트워크로 분류하지 않고 호출자에게 그대로 올린다. */
class FamilyRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()

    suspend fun uid(): String = auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid

    /** D#2: 기기 시계 대신 서버 시각을 기준으로 계산하기 위한 오프셋. */
    fun serverOffset(): Flow<Long> = db.getReference(".info/serverTimeOffset").values { it.getValue(Long::class.java) ?: 0L }

    suspend fun serverNow(): Long = System.currentTimeMillis() + withTimeout(TIMEOUT_MS) { serverOffset().first() }

    suspend fun createFamily(name: String): String {
        val uid = uid()
        val fid = db.getReference("families").push().key!!
        db.getReference("families/$fid/members/$uid").setValue(member(Role.PARENT, name)).await()
        return fid
    }

    /** FR#4: 발급한 부모가 참여 역할을 정한다. 만료는 서버 시각 기준 10분. 반환: 코드와 만료 시각(서버 기준). */
    suspend fun createInvite(fid: String, role: Role): Pair<String, Long> {
        val code = PairingCode.generate()
        val expiresAt = PairingCode.expiresAt(serverNow())
        db.getReference("pairing/$code").setValue(mapOf("familyId" to fid, "role" to role.key, "expiresAt" to expiresAt)).await()
        return code to expiresAt
    }

    /** 사용했거나 닫은 초대 코드는 지운다. 만료된 코드가 쌓이지 않게 한다. */
    suspend fun deleteInvite(code: String) {
        db.getReference("pairing/$code").removeValue().await()
    }

    /** FR#5: 코드가 가리키는 가족에 코드의 역할로 참여한다. 반환: 가족 ID 와 역할. */
    suspend fun join(rawCode: String, name: String): Pair<String, Role> {
        val uid = uid()
        val code = PairingCode.normalize(rawCode)
        val snap = db.getReference("pairing/$code").get().await()
        val fid = snap.child("familyId").getValue(String::class.java) ?: throw JoinException(JoinException.Reason.NOT_FOUND)
        val role = Role.of(snap.child("role").getValue(String::class.java)) ?: throw JoinException(JoinException.Reason.NOT_FOUND)
        if ((snap.child("expiresAt").getValue(Long::class.java) ?: 0) <= serverNow()) throw JoinException(JoinException.Reason.EXPIRED)
        db.getReference("families/$fid/members/$uid").setValue(member(role, name) + ("code" to code)).await()
        return fid to role
    }

    fun family(fid: String): Flow<FamilySnapshot> = db.getReference("families/$fid").values { s ->
        FamilySnapshot(
            members = s.child("members").children.mapNotNull { m ->
                val role = Role.of(m.child("role").getValue(String::class.java)) ?: return@mapNotNull null
                Member(m.key!!, role, m.child("name").getValue(String::class.java).orEmpty())
            },
            live = s.child("children").children.associate { c ->
                val l = c.child("live")
                c.key!! to ChildLive(
                    live = if (l.exists()) {
                        Live(
                            pkg = l.child("pkg").getValue(String::class.java),
                            label = l.child("label").getValue(String::class.java),
                            since = l.child("since").getValue(Long::class.java),
                            title = l.child("title").getValue(String::class.java),
                            url = l.child("url").getValue(String::class.java),
                            screenOn = l.child("screenOn").getValue(Boolean::class.java) ?: false
                        )
                    } else {
                        null
                    },
                    updatedAt = l.child("updatedAt").getValue(Long::class.java),
                    usageGranted = l.child("perms/usage").getValue(Boolean::class.java) ?: true
                )
            },
            apps = s.child("apps").children.associate { it.key!! to it.child("label").getValue(String::class.java).orEmpty() }
        )
    }

    /** FR#6: 멤버와 자녀 데이터를 한 번에 지운다. */
    suspend fun removeMember(fid: String, uid: String) {
        db.getReference("families/$fid").updateChildren(mapOf("members/$uid" to null, "children/$uid" to null)).await()
    }

    /**
     * FR#1 초기화: 자기 멤버 레코드와 기록을 함께 지운다 (자녀가 남긴 기록이 고아가 되지 않게).
     * 이미 보호자가 제거한 기기는 권한이 없어 거부되는데, 자기 레코드도 읽을 수 없을 때만 이미 탈퇴한 것으로 본다.
     * RTDB 쓰기는 오프라인이면 끝나지 않고 대기하므로 시간 제한을 둔다.
     */
    suspend fun leave(fid: String) {
        val uid = uid()
        try {
            withTimeout(TIMEOUT_MS) { db.getReference("families/$fid").updateChildren(mapOf("members/$uid" to null, "children/$uid" to null)).await() }
        } catch (e: DatabaseException) {
            // 거부가 '이미 제거됨' 때문인지 확인한다. 아직 멤버라서 자기 레코드를 읽을 수 있다면 진짜 실패다.
            if (e.message?.contains("Permission denied", ignoreCase = true) != true) throw e
            if (!alreadyRemoved(runCatching { withTimeout(TIMEOUT_MS) { db.getReference("families/$fid/members/$uid").get().await().exists() } })) throw e
        }
    }

    companion object {
        // RTDB 는 오프라인이면 쓰기·읽기가 끝나지 않고 대기하므로, 화면이 멈추지 않게 시간 제한을 둔다.
        private const val TIMEOUT_MS = 10_000L

        /** 탈퇴가 거부된 뒤 자기 레코드 조회 결과로 '이미 제거됨'을 판정한다. 네트워크 실패는 판정 불가라 실패로 본다. */
        fun alreadyRemoved(read: Result<Boolean>): Boolean = read.fold(
            onSuccess = { exists -> !exists },
            onFailure = { it.message?.contains("permission", ignoreCase = true) == true }
        )
    }

    private fun member(role: Role, name: String) = mapOf("role" to role.key, "name" to name.trim().take(40), "joinedAt" to ServerValue.TIMESTAMP)
}

val Role.key get() = name.lowercase()

fun Role.Companion.of(key: String?): Role? = Role.entries.firstOrNull { it.key == key }

private fun <T> com.google.firebase.database.Query.values(map: (DataSnapshot) -> T): Flow<T> = callbackFlow {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            trySend(map(snapshot))
        }

        override fun onCancelled(error: DatabaseError) {
            close(error.toException())
        }
    }
    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}
