# 춘천버스 위젯 코드 안내

화면을 고칠 때는 `feature`부터 찾으면 됩니다. 데이터 저장·통신 코드는 `data`에만 둡니다.

```
app/src/main/java/com/cheon/ccbuswidget
├── feature
│   ├── settings       앱 설정 화면
│   ├── widgetconfig   위젯 추가·편집 화면
│   └── stopdetail     정류장 지도, 정류장·노선·검색 화면
├── data
│   ├── api            공공데이터(TAGO) 통신
│   ├── local          API 키, 위젯 설정, 즐겨찾기 저장
│   └── model          정류장·노선·버스 데이터 형태
├── widget             실제 홈 화면 위젯 갱신·렌더링
└── ui/theme           색상, 글꼴, 화면 크기 토큰
```

## 자주 고칠 곳

| 바꾸려는 내용 | 파일 |
| --- | --- |
| API 키·도시 코드 설정 화면 | `feature/settings/SettingsActivity.kt` |
| 위젯 추가·모양 설정 | `feature/widgetconfig/WidgetConfigurationActivity.kt` |
| 정류장 도착 정보 UI | `feature/stopdetail/StopDetailStopUi.kt` |
| 노선 상세·타임라인 UI | `feature/stopdetail/StopDetailRouteUi.kt` |
| 검색 UI | `feature/stopdetail/StopDetailSearchUi.kt` |
| 메인 하단 바·메뉴 팝업 | `feature/stopdetail/MainNavigationUi.kt` |
| 즐겨찾기 창·드래그 확장 애니메이션 | `feature/stopdetail/FavoritesSheet.kt` |
| 즐겨찾기 목록·표시용 보조 정보 | `feature/stopdetail/FavoritesList.kt` |
| 즐겨찾기 목록 뒤를 흐리는 하단 바 | `feature/stopdetail/ContentBackdrop.kt` |
| 지도·마커·유리 효과 | `feature/stopdetail/StopDetailMapUi.kt` |
| 화면 전환·데이터 불러오기 | `feature/stopdetail/StopDetailScreen.kt` |
| 공공데이터 요청 | `data/api/TagoApi.kt` |
| 즐겨찾기·설정 저장 | `data/local/WidgetStore.kt` |

## 새 메인 UX 확인 방법

1. 앱 아이콘으로 실행하면 지도가 선택되어야 합니다. 위젯으로 실행하면 해당 정류장이 열립니다.
2. 즐겨찾기를 누르면 지도는 테두리 아이콘, 즐겨찾기는 채운 아이콘으로 바뀝니다.
3. 저장한 정류장·노선이 목록에 표시되고, 항목을 열었다가 뒤로 가면 목록으로 돌아옵니다.
4. 즐겨찾기 손잡이를 올리면 같은 목록이 전체 화면으로 커집니다. 블러가 강해지다가 단색으로 채워집니다. 내리거나 뒤로 가면 접힙니다.
5. 확장 중 방향을 바꾸어 끌어도 창이 튀지 않아야 합니다. 목록 끝은 하단 바 위까지 스크롤할 수 있습니다.
6. 검색에서 뒤로 가면 직전 지도/즐겨찾기 화면으로 돌아옵니다.
7. 메뉴의 설정은 API 키 페이지를 엽니다. 계정 설정·더미 두 개는 준비 중 안내만 표시합니다.
8. 밝은/어두운 테마, 글자 크기 확대, 제스처/3버튼 내비게이션에서 잘림을 확인합니다.

즐겨찾기 전환은 정류장·노선 전환과 독립되어 있습니다. 공통 손잡이만 Figma의 36×5dp, 위쪽 12dp 규격을 공유합니다.
새 아이콘의 Figma 원본 SVG는 `design/figma/main-navigation`에 있고 Android 벡터 리소스는 `ic_nav_*`입니다.
