package io.github.hwanyu365.argos.data

// spec D#1: RTDB 키는 '.' 을 허용하지 않고 패키지명에는 ',' 가 올 수 없어 역변환이 유일하다.
object PkgKey {
    fun encode(pkg: String): String = pkg.replace('.', ',')

    fun decode(key: String): String = key.replace(',', '.')
}
