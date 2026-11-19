# StarRing

커플이 함께 은하계를 키우는 안드로이드 앱.

## 기술 스택
- **Java만 사용 (Kotlin 금지)** — 새 파일도 전부 `.java`. Gradle 스크립트도 Groovy(`build.gradle`) 유지.
- minSdk 26 / targetSdk 37, Java 11 (`compileOptions`)
- 화면 렌더링: 2D Canvas 커스텀 View (`onDraw` 기반, 게임 엔진/OpenGL 사용 안 함)
- Firebase Auth(이메일/비밀번호) + Firestore (Firebase BoM으로 버전 관리)
- 사진 업로드/저장: Cloudinary (`cloudinary-android`)
- 의존성 버전은 `gradle/libs.versions.toml` + `app/build.gradle`

## 빌드
```
./gradlew assembleDebug        # 빌드
./gradlew installDebug         # 연결된 기기/에뮬레이터에 설치
./gradlew test                 # 유닛 테스트
```

## 패키지 구조 (`app/src/main/java/com/example/starring/`)
| 패키지 | 역할 |
|---|---|
| `auth` | 로그인/회원가입 (Firebase Auth) |
| `couple` | 커플 연동, 초대코드, 은하계 이름 설정 |
| `galaxy` | 은하계 메인 화면, 별 계산/렌더링 커스텀 View, 태양계 메뉴바 |
| `planet` | 월별 행성, 사진(Cloudinary), 외계인 방문 하트 |
| `court` | 커뮤니티 판사 — 블랙홀, 익명 투표 |
| `constellation` | 매월 1일 별자리 |
| `common` | 공용 유틸 (시간 계산, 시드 랜덤 등) |
| `data` | Firestore 모델/리포지토리 |

루트에는 `StarRingApp`(Application, Cloudinary `MediaManager` 초기화)과 `MainActivity`가 있음.

### 현재 상태
- 각 기능 패키지는 `.gitkeep`만 있는 빈 뼈대.
- `MainActivity`는 Auth/Firestore 연결 테스트용 임시 화면 (`connection_tests` 컬렉션 사용) — 실제 화면 만들면 교체 대상.
- `StarRingApp`의 Cloudinary `cloud_name`은 아직 플레이스홀더.

## 핵심 설계 규칙
- **시간으로 계산하는 값은 DB에 저장하지 않는다**: 별 개수/위치, 블랙홀이 삼킨 별, 별자리, D-day.
  DB에는 기준 시각(은하계 탄생 시각, 블랙홀 생성/해결 시각 등)만 저장하고 나머지는 계산으로 도출.
- **시간 기준은 Firestore `serverTimestamp`** — 기기 시간(`System.currentTimeMillis()`)을 기준값으로 쓰지 않는다.
- **별 위치는 시드 고정 랜덤** — `galaxyId + index`로 시드를 만들어 `new Random(seed)`로 생성 → 언제 그려도 같은 자리.
- **serverTimestamp는 쓰기 직후 로컬 스냅샷에서 null** → 화면에 그리거나 계산할 때 반드시 null 체크 (null이면 그리지 않거나 대기).

## 기능 목록 (구현 순서)
1. **커플 연동** — 6자리 초대코드로 연결 → 은하계 이름 설정
2. **우주 시스템**
   - 매일 애기별 / 매달 큰 별 / 매년 초신성 / 3년마다 백색왜성
   - 매월 1일 별자리
   - 태양계 모양 메뉴바, 화면 터치로 메뉴 토글
   - 우측 상단 "OO은하계 탄생 OO일"
3. **커뮤니티 판사** — 블랙홀 생성 → 다른 커플이 익명 투표, 30분마다 애기별 1개 삼킴, 해결 시 삼킨 별 반환
4. **행성** — 월 1개, 사진 저장, 클릭 시 확대, 외계인 방문 하트
5. **채팅** — 마지막 단계

## 작업 규칙
- 한 번에 기능 하나씩 구현하고, 동작 확인 후 커밋.
- 코드 작성 후 무엇을 했는지 **한국어로 짧게** 설명.
- 비밀 값(키스토어, 서비스 계정 키, `.env`, `secrets.properties`)은 커밋하지 않는다 (`.gitignore`에 등록됨).
