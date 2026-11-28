# StarRing

커플이 함께 은하계를 키우는 안드로이드 앱.

## 기술 스택
- **Java만 사용 (Kotlin 금지)** — 새 파일도 전부 `.java`. Gradle 스크립트도 Groovy(`build.gradle`) 유지.
- minSdk 26 / targetSdk 37, Java 11 (`compileOptions`)
- 화면 렌더링: 2D Canvas 커스텀 View (`onDraw` 기반, 게임 엔진/OpenGL 사용 안 함)
- Firebase Auth(이메일/비밀번호) + Firestore (Firebase BoM으로 버전 관리)
- 사진 업로드/저장: Cloudinary unsigned 업로드 (`PlanetCreateActivity`의 `CLOUD_NAME`/`UPLOAD_PRESET` 상수 사용)
- 의존성 버전은 `gradle/libs.versions.toml` + `app/build.gradle`

## 빌드
```
./gradlew assembleDebug        # 빌드
./gradlew installDebug         # 연결된 기기/에뮬레이터에 설치
./gradlew test                 # 유닛 테스트
```

## 패키지 구조 (`app/src/main/java/com/example/starring/`)
| 위치 | 역할 |
|---|---|
| 루트 | `StarRingApp`(Application), `MainActivity`(Splash 분기), 은하계 메인(`GalaxyActivity`, `GalaxyView` — 별·별자리·특수천체 렌더링), 은하계 설정/로딩/소멸(`GalaxySetupActivity`, `GalaxyLoadingActivity`, `GalaxyDestroyActivity`), 행성(`PlanetCreateActivity`, `PlanetDetailActivity`, `PlanetWorldView`, `PlanetMonth`), `SpaceBackdrop` |
| `auth` | 로그인/회원가입 (Firebase Auth, ViewModel) |
| `couple` | 커플 연동 화면, 초대코드, 만난 날 검증(`StartDatePolicy`), 연동 해제(`UnlinkHelper`) |
| `court` | 우주 재판장 — 블랙홀 글쓰기/투표/화해, 삼킨 별 계산(`StarConsumption`), 연출(`BlackholeEffect`, `AlienFightView`) |
| `common` | 공용 유틸 — 날짜 계산(`GalaxyDates`), 화면 이동(`Navigator`), 에러 문구(`ErrorMessages`), 블랙홀 잠금(`BlackholeLock`) 등 |
| `chat` | 커플 채팅(`ChatActivity`, 메시지 검사 `ChatPolicy`). 메뉴 목성에서 진입 |
| `data` | Firestore 필드 상수(`FirestoreSchema`), `UserRepository`, `GalaxyRepository` |
| `galaxy` | `GalaxyPlaceholderActivity`만 남음 (예전 임시 메인, Manifest 등록만 되어 있고 화면 이동에서는 안 씀) |
| `planet`, `constellation` | 비어 있음(`.gitkeep`). 행성은 루트, 별자리는 `GalaxyView`에 구현 |

### 화면 흐름
`MainActivity`(Splash: 로그인·은하계 상태 확인) → `LoginActivity` / `CoupleLinkActivity` → (새 은하계) `GalaxySetupActivity`(이름·만난 날 입력) → `CoupleLinkActivity`(초대코드 대기 → 합류 시 바로 active) → `GalaxyLoadingActivity` → `GalaxyActivity`.
화면 이동은 `common/Navigator`를 통한다 (`toMain()` = `GalaxyLoadingActivity`).

### Firestore 컬렉션
`users`, `galaxies`(+ 하위 `planets`(+ `likes`), `messages`), `inviteCodes`, `courtPosts`(+ 하위 `votes`, `lessons`). 보안 규칙은 루트 `firestore.rules` (팀장 관리, 콘솔 수동 배포). 인덱스는 `firestore.indexes.json` (`firebase deploy --only firestore:indexes`로 인덱스만 배포).

### 현재 상태
- 커플 연동·은하계 메인·우주 재판장·행성·행성 둘러보기·커플 채팅이 구현됨.
- 블랙홀(화해 전 재판 글)이 있는 동안 행성 생성·사진 추가·이름 변경·둘러보기·하트·행성 편집은 앱에서 막힘(`common/BlackholeLock`). 투표/화해·재판장·로그아웃·소멸·채팅·행성 보기는 허용.
- 은하계 이름은 생성할 때 입력한다. `CoupleLinkActivity`의 NAMING 패널/`nameGalaxy`는 코드에 남아 있지만 현재 흐름에서는 쓰지 않음 (rules상 이름 변경 불가).
- `StarRingApp`의 Cloudinary `MediaManager` 초기화는 `cloud_name`이 여전히 플레이스홀더이고 실제로 쓰이지 않음. 사진 업로드는 `PlanetCreateActivity`가 자체 설정으로 처리.

## 핵심 설계 규칙
- **시간으로 계산하는 값은 DB에 저장하지 않는다**: 별 개수/위치, 블랙홀이 삼킨 별, 별자리, D-day.
  DB에는 기준 시각(은하계 탄생 시각, 블랙홀 생성/해결 시각 등)만 저장하고 나머지는 계산으로 도출.
- **별 개수 / 별자리 / D-day 계산 기준은 `galaxies.datingStartDate`(만난 날)** — `bornAt`(커플 연동 후 활성화 시각)이 아님.
  datingStartDate는 사용자가 고른 날짜를 `"yyyy-MM-dd"` 문자열로 저장 (시간대 없는 날짜. serverTimestamp 원칙의 예외: 사용자 입력 날짜).
  rules는 형식만 검사하므로 실존 날짜·1950-01-01~오늘 범위는 앱에서 `couple/StartDatePolicy`로 검사.
  계산은 `common/GalaxyDates` 사용 (`parseDate`, `elapsedDays` 등). datingStartDate가 없는 기존 은하계는 bornAt 날짜로 대체 (`GalaxyDates.effectiveStart`).
- **시간 기준은 Firestore `serverTimestamp`** — 기기 시간(`System.currentTimeMillis()`)을 기준값으로 쓰지 않는다.
- **별 위치는 시드 고정 랜덤** — `galaxyId + index`로 시드를 만들어 `new Random(seed)`로 생성 → 언제 그려도 같은 자리.
- **serverTimestamp는 쓰기 직후 로컬 스냅샷에서 null** → 화면에 그리거나 계산할 때 반드시 null 체크 (null이면 그리지 않거나 대기).

## 기능 목록 (구현 순서)
1. **커플 연동** — 은하계 이름·만난 날 입력 후 생성 → 6자리 초대코드로 연결
2. **우주 시스템**
   - 매일: 애기별 / 1달: 큰 별 / 100일: 특수천체(덜 화려) / 1년: 특수천체(많이 화려)
     (초안이었던 "1년 초신성 / 3년 백색왜성"은 폐기)
   - 매월 1일 별자리
   - 태양계 모양 메뉴바, 화면 터치로 메뉴 토글
   - 우측 상단 "OO은하계 탄생 OO일" (datingStartDate 기준)
3. **커뮤니티 판사** — 블랙홀 생성 → 다른 커플이 익명 투표, 30분마다 애기별 1개 삼킴, 해결 시 삼킨 별 반환
4. **행성** — 월 1개, 사진 저장, 클릭 시 확대, 외계인 방문 하트
5. **채팅** — `galaxies/{galaxyId}/messages` (senderUid, text 1~500자, createdAt serverTimestamp). 최근 50개 실시간 + 위로 스크롤 시 50개씩. 푸시 알림 없음

## 작업 규칙
- 한 번에 기능 하나씩 구현하고, 동작 확인 후 커밋.
- 코드 작성 후 무엇을 했는지 **한국어로 짧게** 설명.
- 비밀 값(키스토어, 서비스 계정 키, `.env`, `secrets.properties`)은 커밋하지 않는다 (`.gitignore`에 등록됨).

### 작업 로그
- 작업 단위가 끝날 때마다(기능 구현 완료, 커밋, merge, 중단 포함) 루트의 `work-log.md`를 갱신한다.
- 기록할 것: 무엇을 했는지, 변경한 파일, 검증 결과, 남은 일/막힌 것, 팀원에게 전달할 사항.
- 누적이 아니라 현재 상태를 반영한다. 완료된 항목은 체크하고, 해결된 이슈는 정리한다.
- `work-log.md`는 `.gitignore`에 등록되어 있어 커밋하지 않는다 (개인 작업 메모).
