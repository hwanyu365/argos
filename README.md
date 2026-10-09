# argos

자녀 Android 기기에서 **지금 쓰고 있는 앱**, 보고 있는 **영상 제목과 웹 주소**, 그리고 **한 달간 앱별 사용 시간**을 부모 Android 기기에서 확인하는 앱입니다.

- 서버가 없습니다. 각자 만든 Firebase 무료(Spark) 프로젝트 하나로 동작합니다
- 자녀 기기에는 감시 중임을 알리는 알림이 항상 표시됩니다
- 접근성 서비스로 브라우저 주소를 읽기 때문에 Play Store 에는 올릴 수 없고, APK 를 직접 설치합니다

> 상태: 개발 중. 요구사항과 동작 규칙은 [docs/specification.md](docs/specification.md) 에 있습니다.

## 설치해서 쓰기

### 1. Firebase 프로젝트 준비 (무료)

1. [Firebase 콘솔](https://console.firebase.google.com) 에서 프로젝트를 만듭니다. 요금제는 Spark(무료) 그대로 둡니다
2. **Authentication** > 로그인 방법 > **익명** 사용 설정
3. **Realtime Database** > 데이터베이스 만들기 (잠금 모드로 시작)
4. 프로젝트 설정 > 내 앱 > **Android 앱 추가**, 패키지명 `io.github.hwanyu365.argos`
   - `google-services.json` 은 받지 않아도 됩니다. 화면에 보이는 API 키·앱 ID·프로젝트 ID 만 필요합니다
5. 보안 규칙 배포

   ```sh
   npm --prefix firebase ci
   npx --prefix firebase firebase login
   npx --prefix firebase firebase deploy --only database --project <프로젝트 ID>
   ```
6. (권장) Google Cloud 콘솔 > API 및 서비스 > 사용자 인증 정보에서 API 키를 Android 앱(패키지명 + 서명 SHA-1)으로 제한합니다
   - Firebase API 키는 비밀번호가 아니라 식별자입니다. 데이터는 보안 규칙(`database.rules.json`)과 가족 단위 인증이 보호합니다

### 2. 빌드

- 필요 도구: JDK 21, Android SDK (API 36), Node.js 22 (규칙 테스트·배포용)
- `local.properties.example` 을 `local.properties` 로 복사하고 값을 채웁니다. 같은 이름의 환경변수로도 줄 수 있습니다

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

설정값 없이 빌드하면 앱이 "Firebase 설정이 필요합니다" 화면을 띄웁니다.

### 3. 기기 설정

1. 부모와 자녀 기기 모두에 APK 를 설치합니다
2. 부모 기기: 역할 "부모" → 가족 만들기 → 초대 코드/QR 표시
3. 자녀 기기: 역할 "자녀" → QR 스캔 또는 코드 입력 → 권한 체크리스트를 순서대로 허용
   - Android 13 이상은 접근성·알림 접근을 켜기 전에 **설정 > 앱 > Argos > ⋮ > 제한된 설정 허용**이 필요합니다

## 개발

이 저장소는 사람이 이슈와 리뷰를 맡고 구현·검증은 Claude Code 가 맡는 방식으로 운영합니다. 규칙은 [CLAUDE.md](CLAUDE.md) 에 있습니다.

```sh
./gradlew check            # ktlint + Android lint + 단위 테스트
npm --prefix firebase test # 보안 규칙 테스트 (Firebase Emulator, JDK 21 필요)
```

- 작업 흐름: 이슈 작성 → 로컬 Claude Code 또는 이슈 코멘트에 `@claude` → draft PR → CI + Claude 리뷰 → merge
- 배포: `v*` 태그를 push 하면 서명된 APK 가 GitHub Releases 에 올라갑니다

### 저장소 소유자 초기 설정 (fork 해서 운영할 때)

| 할 일 | 방법 |
| --- | --- |
| Claude GitHub App 설치 | 로컬 Claude Code 에서 `/install-github-app` |
| `CLAUDE_CODE_OAUTH_TOKEN` | `claude setup-token` 으로 발급 → Secrets 에 등록 |
| `ARGOS_FIREBASE_API_KEY`, `_APP_ID`, `_PROJECT_ID`, `_DATABASE_URL` | release 빌드용 |
| `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | release 서명용. `base64 -w0 release.jks` |
| `FIREBASE_SERVICE_ACCOUNT` | 규칙 자동 배포용 서비스 계정 JSON (역할: Firebase Realtime Database Admin) |
| main 브랜치 보호 | CI(`android`, `rules`) 통과 필수, PR 로만 merge |

## License

MIT
