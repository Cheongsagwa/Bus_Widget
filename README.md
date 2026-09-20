# 춘천버스 위젯 (ChuncheonBusWidget)

춘천시 시내버스 정류장의 도착 시간을 안드로이드 홈 화면 위젯으로 보는 앱입니다.
카카오맵 버스 위젯처럼 **정류장 1곳 + 원하는 노선 여러 개**를 지정해서 쓰는 방식입니다.

---

## 1. 먼저 할 일 — 공공데이터 서비스키 발급

이 앱은 국토교통부(TAGO) 오픈 API를 씁니다. **본인 인증키가 있어야 동작합니다.**

1. [공공데이터포털](https://www.data.go.kr) 회원가입 / 로그인
2. **[국토교통부_(TAGO)_버스도착정보](https://www.data.go.kr/data/15098530/openapi.do)** → `활용신청`
3. **[국토교통부_(TAGO)_버스정류소정보](https://www.data.go.kr/data/15098534/openapi.do)** → `활용신청`
   - 활용목적은 "앱개발(정보제공)" 정도로 적으면 됩니다. 자동 승인이며 보통 몇 분 내에 처리됩니다.
4. `마이페이지 > 개발계정 > 일반 인증키(Encoding)` 값을 복사
5. 앱을 실행해 **서비스키** 칸에 붙여넣고 저장

> 두 서비스 모두 신청해야 합니다. 하나만 신청하면 정류장 검색이나 도착정보 중 하나가 실패합니다.
> 무료 개발계정은 하루 1,000회 호출 제한이 있습니다. (위젯 갱신 15분 주기 기준으로는 충분)

춘천시 **도시코드는 32010** 이며 앱에 기본값으로 들어가 있습니다.

---

## 2. 빌드

1. Android Studio(Ladybug 이상 권장)에서 `File > Open` → 이 폴더 선택
2. Gradle Sync가 끝나면 `Run ▶` (실기기 또는 에뮬레이터)
   - 위젯은 실기기에서 확인하는 편이 훨씬 낫습니다.

| 항목 | 값 |
| --- | --- |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 35 |
| AGP / Gradle | 8.7.3 / 8.11.1 |
| Kotlin | 2.0.21 (Compose Compiler 플러그인) |

---

## 3. 사용법

1. 앱 실행 → 서비스키 저장
2. 홈 화면 빈 곳 길게 누르기 → `위젯` → **춘천버스 위젯** 을 홈에 배치
3. 설정 화면이 자동으로 열립니다
   - **정류장**: 이름으로 검색 (예: `춘천역`, `명동`) 후 선택
   - **노선**: 그 정류장을 지나는 노선이 칩으로 나옵니다. 원하는 것만 고르면 **고른 순서대로** 표시됩니다.
     (아무것도 안 고르면 도착이 빠른 순서대로 표시)
   - **모양**: 배경 불투명도 / 어두운 배경 / 표시 줄 수
4. 저장하면 위젯에 도착 정보가 채워집니다

- 위젯의 **새로고침 아이콘** → 즉시 갱신
- 위젯의 **정류장 이름 탭** → 설정 다시 열기 (위젯 길게 누르기로도 가능)
- 자동 갱신은 WorkManager 15분 주기입니다 (안드로이드가 허용하는 최소 주기)

---

## 4. 위젯 배경 블러

두 가지 모드를 넣어 뒀고 설정 화면에서 전환할 수 있습니다. 삼성 기기에서는 기본이 One UI 모드입니다.

### (A) One UI Home 네이티브 블러 — 삼성 One UI 7.0+ (기본값)

One UI Home 런처는 조건을 만족하는 위젯을 만나면 **위젯 뒤 배경화면을 직접 캡처해 블러 처리한 뒤
지정한 배경색으로 틴팅해서 깔아 줍니다.** 위젯이 블러를 그리는 게 아니라 런처가 그려 주는 방식이라
카카오맵 위젯과 같은 결과가 나옵니다. 삼성 공식 문서는 없고, 아래 세 조건만 맞으면 됩니다.

1. **루트 뷰의 ID 가 `@android:id/background`** — `res/layout/widget_bus.xml`
2. **배경색 알파가 1~254** — 0(완전 투명)이나 255(완전 불투명)면 블러가 꺼집니다
3. **위젯 메타데이터에 `app:widgetStyle` / `app:widgetSize` 선언** — `res/xml/bus_widget_info.xml`,
   속성 자체는 `res/values/attrs.xml` 에서 직접 정의 (삼성 프레임워크가 이름으로 읽어 감)

```xml
<!-- res/xml/bus_widget_info.xml -->
<appwidget-provider
    xmlns:app="http://schemas.android.com/apk/res-auto"
    ...
    app:widgetStyle="colorful"
    app:widgetSize="small|wideSmall|medium|large" />
```

```kotlin
// WidgetRenderer.applyBackground()
val alpha = (percent * 255 / 100).coerceIn(1, 254)
views.setInt(android.R.id.background, "setBackgroundColor", Color.argb(alpha, r, g, b))
```

설정의 **배경 불투명도**는 이 모드에서 "블러 위에 덮는 색의 진하기"가 됩니다 (5~95% 로 제한).

> 참고: [thatjoshguy67/blur-widget-demo](https://github.com/thatjoshguy67/blur-widget-demo) 의 PoC 와 위키.

### (B) 반투명 패널 — 그 외 런처

안드로이드 표준 API만으로는 서드파티 위젯이 배경을 실시간 블러 처리할 수 없습니다
(위젯은 런처 프로세스에서 `RemoteViews` 로 그려져 `RenderEffect` 를 쓸 수 없음).
그래서 삼성이 아닌 기기에서는 라운드 코너 + 반투명 패널(`R.id.widget_bg`)로 비슷한 느낌만 냅니다.

## 5. 구조

```
app/src/main/java/com/cheon/ccbuswidget/
├─ MainActivity.kt              서비스키/도시코드 설정, 사용 안내
├─ data/
│  ├─ Models.kt                 BusStop / BusRoute / BusArrival / WidgetConfig
│  ├─ TagoApi.kt                TAGO 오픈 API 호출 + JSON 파싱
│  └─ WidgetStore.kt            위젯별 설정 저장 (SharedPreferences)
├─ ui/
│  ├─ ConfigureActivity.kt      위젯 설정 화면 (Compose)
│  └─ theme/Theme.kt
└─ widget/
   ├─ BusWidgetProvider.kt      AppWidgetProvider (갱신/삭제/새로고침 수신)
   ├─ WidgetRenderer.kt         RemoteViews 구성 + API 호출
   └─ RefreshWorker.kt          WorkManager 즉시/주기 갱신

app/src/main/res/
├─ layout/widget_bus.xml        위젯 본체
├─ layout/widget_bus_row.xml    노선 한 줄
├─ drawable/widget_bg_shape.xml 반투명 배경 패널
└─ xml/bus_widget_info.xml      위젯 메타데이터
```

### 쓰는 API

| 용도 | 오퍼레이션 |
| --- | --- |
| 정류장 이름 검색 | `BusSttnInfoInqireService/getSttnNoList` |
| 좌표 주변 정류장 | `BusSttnInfoInqireService/getCrdntPrxmtSttnList` |
| 정류장 경유 노선 | `BusSttnInfoInqireService/getSttnThrghRouteList` |
| 도착 예정 정보 | `ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList` |

도착정보는 정류장 전체를 한 번에 받아 앱에서 노선 번호로 걸러 씁니다. (호출 1회로 여러 노선 처리)

---

## 6. 자주 겪는 문제

| 증상 | 원인 / 해결 |
| --- | --- |
| `등록되지 않은 서비스키입니다` | 활용신청 승인 전이거나 키 오타. 포털 마이페이지에서 승인 상태 확인 |
| 정류장 검색 결과 없음 | 정류장 정식 명칭이 다를 수 있음. 짧은 키워드로 검색 (`춘천역` → `춘천`) |
| `지금은 도착 예정 정보가 없습니다` | 막차 이후이거나 해당 노선이 현재 운행 중이 아님 (정상 동작) |
| 위젯이 자동으로 안 바뀜 | 배터리 최적화 대상에서 앱 제외 (설정 > 배터리 > 앱 배터리 사용량) |
| 다른 도시에서 쓰고 싶음 | 앱 첫 화면의 도시코드만 바꾸면 됩니다 (예: 원주 32020) |

---

## 7. 더 해볼 만한 것

- 정류장 선택 시 GPS 주변 검색 (`TagoApi.nearbyStops` 이미 구현되어 있음, 위치 권한만 추가하면 됨)
- 도착 임박(2분 이하) 시 노선 번호 색상 강조
- 위젯 탭 시 카카오맵 해당 정류장으로 이동하는 딥링크
- 즐겨찾기 정류장을 앱 본화면에 목록으로 표시
