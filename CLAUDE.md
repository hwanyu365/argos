# argos

자녀 Android 기기의 앱 사용(현재 앱, 영상 제목·URL, 기간별 앱 사용 시간)을 부모 Android 기기에서 확인하는 앱이다.
서버 없이 사용자 각자의 Firebase 무료(Spark) 프로젝트를 쓰며, 소스는 GitHub public 저장소(`hwanyu365/argos`)로 공개된다.

## SW 스펙 문서

- 경로: `./docs/specification.md` (전역 기본값 `_docs/spec_argos.md` 를 재정의) → 스펙 변경을 PR `git diff` 로 리뷰하기 위함
- 작성 규칙: `E:\01_dev\_docs\rules_spec.md`
- 작업 기준 색인

  | 작업 | 스펙 위치 |
  | --- | --- |
  | 페어링·가족·권한 규칙 | §3.2 FR#3~6, §4.2, §4.3 |
  | 자녀 기기 수집·세션 규칙 | §3.2 FR#7~14, §6.1 |
  | 일별 집계 | §6.2 |
  | 영상 제목·URL 추출 | §6.3 |
  | 부모 화면 | §3.2 FR#15~18, §5 |
  | 감시 중단 판정 | §6.4 |
  | 빌드 설정 주입 | §4.4 |

## 기술 스택

- Kotlin, Jetpack Compose + Material 3, 단일 `app` 모듈, minSdk 26 / targetSdk 35
- Firebase Auth(Anonymous) + Realtime Database, Room, WorkManager, Compose Navigation, ZXing
- 위 목록 밖의 의존성 추가는 PR 본문에 이유를 적는다

## 반드시 지킬 것

- **Firebase 설정값·서명 키를 커밋하지 않는다.** 값은 `local.properties` 또는 환경변수 `ARGOS_FIREBASE_*` 로만 주입한다 (스펙 §4.4, NFR#5)
  - `google-services` 플러그인을 쓰지 않는다. `FirebaseOptions` 로 수동 초기화한다
  - 설정값이 없어도 빌드·단위 테스트는 통과해야 한다 → CI 와 fork PR 이 secret 없이 돈다
- 실제 보안은 `database.rules.json` 이 담당한다. 데이터 경로를 바꾸면 규칙과 `firebase/` 규칙 테스트를 같이 바꾼다
- 자녀 기기의 상시 알림을 숨기는 기능은 만들지 않는다 (NFR#6)

## 개발 절차

- SDD + TDD: 이슈 → 스펙 갱신 → TC 작성(Red) → 구현(Green) → Refactor
- 테스트 위치: Android Gradle 이 `src/test/` 를 강제하므로 전역 규칙(소스 옆 `{file}.test.ts`) 대신 `app/src/test/java/<같은 패키지>/{Class}Test.kt` 로 패키지를 미러링한다
- 테스트 이름은 스펙의 `TC#n` 으로 시작한다 (예: `` `TC#12 같은 앱 RESUMED 연속은 세션 유지`() ``)
- 핵심 로직은 Android 의존성 없는 순수 Kotlin 으로 두어 JVM 단위 테스트로 검증한다

## 검증 (완료 선언 전 반드시 실행)

```sh
./gradlew check            # ktlint + Android lint + 단위 테스트
npm --prefix firebase test # 보안 규칙 (Firebase Emulator)
```

- JDK 21 이 필요하다 (firebase-tools 요구사항이며, Gradle 도 같은 JDK 로 통일). 로컬 경로는 `.claude/settings.local.json` 의 `JAVA_HOME` 으로 둔다
- ktlint 위반은 `./gradlew ktlintFormat` 으로 고친다
- CI(`.github/workflows/ci.yml`)도 같은 명령을 실행한다
- 완료 판정은 스펙 §3·§4·§6 항목별로 구현 위치와 TC 를 대조한다 (rules_spec §2.2)

## GIT

- 티켓: GitHub Issues. 표기 `GH-{번호}`
- 브랜치: `{feat|bugfix|chore}/GH-{번호}` / 워크트리: `../argos-GH-{번호}`
- 커밋 메시지: 전역 규칙과 같고, `[JIRA]` 자리에 `[ISSUE]` 를 쓴다

  ```
  feat:[GH-12] 부모 화면에 영상 제목 표시

  [ISSUE] https://github.com/hwanyu365/argos/issues/12
  [DESC]
  [요구사항]
  - ...
  [구현사항]
  - ...
  ```
- PR 은 draft 로 생성하고 `.github/pull_request_template.md` 를 채운다
- 이 프로젝트는 agent 자동화 개발이다 → 전역 규칙(커밋·PR 은 지시 시에만)을 대체한다
  - 하위 이슈가 끝나면 묻지 않고 커밋 → draft PR → CI·Claude 리뷰 반영 → ready → merge
  - 보고는 상위 이슈(UC) 단위로 한다
  - 예외(먼저 묻는다): 스펙에 없는 결정, 비용·보안 설정 변경, 실사용 기기의 데이터 삭제
- `main` 은 보호 브랜치다 (CI `android`·`rules` 필수, PR 로만 merge, 관리자 포함)
- 하위 이슈 브랜치는 `feat/GH-{상위}-{하위}`, 커밋 1행은 `feat:[GH-{상위}][GH-{하위}] ...`
- `database.rules.json` 이 main 에 merge 되면 실제 Firebase 프로젝트에 자동 배포된다
