package com.cheon.ccbuswidget.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ┌──────────────────────────────────────────────────────────────┐
 * │  앱 화면(Compose)의 크기·간격·글자 크기를 모아 둔 곳입니다.    │
 * │  Figma 의 px 값을 그대로 dp, 글자는 sp 로 넣으면 됩니다.       │
 * │                                                              │
 * │  · 색상       → res/values/colors.xml                        │
 * │  · 위젯 치수  → res/values/dimens.xml                        │
 * └──────────────────────────────────────────────────────────────┘
 */
object Tokens {

    /**
     * 지도 위에 떠 있는 유리 표면 (버튼 · 시트 · 더보기 패널 공통).
     * Figma: fill #80F1F1F3, DROP_SHADOW blur 65 / #40000000, BACKGROUND_BLUR 24
     */
    object Glass {
        val cornerRadius = 32.dp
        /** Figma 그림자 blur 65 를 안드로이드 elevation 으로 옮긴 값 */
        val shadowElevation = 16.dp
        /** Figma BACKGROUND_BLUR 반경 — 더보기 메뉴(별도 창)에서 실제로 적용된다 */
        val blurRadius = 24.dp

        /** 지도 위 원형 버튼 */
        val buttonSize = 50.dp
        val buttonIconSize = 24.dp
        /** 화면 가장자리 ↔ 버튼 */
        val buttonMargin = 10.dp
        /** 상단바 세로 위치 (상태바 아래) */
        val topBarTop = 8.dp
    }


    /** 하단 시트 — 화면에 붙지 않고 좌우가 떠 있는 카드 */
    object Sheet {
        /** 접혀 있을 때 보이는 높이 */
        val peekHeight = 400.dp
        /** 카드 좌우 여백 */
        val sideMargin = 17.dp
        val cornerRadius = 32.dp
        val elevation = 16.dp

        /** 정류장 시트 안쪽 여백 (2열 그리드 기준선) */
        val stopContentPadding = 28.dp
        /** 카드 안쪽 왼쪽 여백 (본문 기준선) */
        val horizontalPadding = 20.dp

        /** 목록 아래쪽 '더 있음' 그라데이션 높이 */
        val fadeHeight = 56.dp

        /** 카드 맨 위 손잡이 */
        val grabberWidth = 36.dp
        val grabberHeight = 5.dp
        val grabberTopPadding = 12.dp

        /** 손잡이 ↔ 헤더 (노선 시트, Figma 14 → 29) */
        val headerTop = 12.dp
        /** 헤더 ↔ 구분선 (노선 시트, Figma 106 → 113) */
        val dividerTop = 7.dp

        /** 정류장 시트: 손잡이 ↔ 정류장 이름 */
        val stopHeaderTop = 11.dp
        /** 정류장 시트: 헤더 ↔ 구분선 */
        val stopDividerTop = 8.dp
        /** 정류장 시트: 구분선 ↔ 2열 그리드 */
        val stopGridTop = 1.dp
        /** 헤더 오른쪽 아이콘 사이 간격 */
        val headerIconGap = 14.dp
        /** 정류장 시트: 손잡이 ↔ 유형 칩 줄 (Figma: 14 → 33) */
        val typeRowTop = 16.dp
        /** 기본 높이에서 이만큼만 끌어올려도 손을 떼면 확장된다 */
        val expandLift = 20.dp
        /** 이만큼만 내려도 손을 떼면 메인화면으로 돌아간다 */
        val collapseDrop = 20.dp
        /** 유형 칩 줄 ↔ 정류장 이름 */
        val stopNameTop = 5.dp

        /**
         * 모핑 시트의 손잡이 줄 높이 (접힘). 즐겨찾기 Figma y=29 와 같다.
         * 정류장·노선 시트는 이 아래에 헤더가 이어지므로 원래 위치(손잡이 12+5 + 헤더 여백)를 맞춰 준다.
         */
        val morphHandleHeight = 29.dp
        /** 모핑 시트가 전체 화면일 때 블러 반경 */
        val morphBlurRadius = 72.dp
    }

    /**
     * 메인화면 하단 플로팅 바 (Figma 67:1125 — 324x57, y=831, 안쪽 6, 버튼 78x45).
     * 배경 #80F1F1F3 · 반경 79 · 그림자 0/7/16.2 #40000000.
     * 선택 칸: #80DDDDDD 배경 + 같은 색 3dp 테두리, 반경 23, 글자 Bold.
     */
    object Toolbar {
        val width = 324.dp
        val height = 57.dp
        val padding = 6.dp
        val itemWidth = 78.dp
        val itemHeight = 45.dp
        val corner = 79.dp
        val itemCorner = 23.dp
        val selectedBorder = 0.dp
        const val selectedAlpha = 0.5f
        /** 아이콘 칸 (위아래 2 + 아이콘 24) */
        val iconBoxHeight = 28.dp
        val iconSize = 24.dp
        val labelText = 10.sp
        /**
         * 화면 맨 아래 ↔ 툴바 아랫변 (Figma 915 - 888 = 27).
         * 내비게이션 바 인셋이 이보다 크면(3버튼 내비게이션) 인셋 위에 붙인다.
         */
        val bottomMargin = 27.dp
        /** 나타날 때 이 크기에서 커지고, 사라질 때 이 크기로 줄어든다 */
        const val hiddenScale = 0.6f
    }

    /**
     * 즐겨찾기 편집의 저장 · 취소 바 (Figma 70:1913 Action bar — 192x57, 툴바와 같은 줄).
     * 안쪽 좌우 24 / 위아래 14, 반경 50, 그림자 0/0/20 #26000000, 글자 18sp SemiBold.
     * 저장 | (구분 칸) | 취소 가 같은 폭으로 나뉜다.
     */
    object ActionBar {
        val width = 192.dp
        val height = 57.dp
        val paddingHorizontal = 24.dp
        val paddingVertical = 14.dp
        val corner = 50.dp
        val shadowElevation = 10.dp
        val text = 18.sp
    }

    /**
     * 현위치 버튼 (Figma 41:738 / 70:1989 / 70:1996 — 40x40, 아이콘 22).
     * 메인: 오른쪽 10, 툴바 윗변 위 30 (761+40 → 831).  정류장·노선: 오른쪽 15, 시트 윗변 위 35.
     */
    object MyLocation {
        val size = 40.dp
        val iconSize = 22.dp
        val margin = 10.dp
        val gapAboveToolbar = 30.dp
        val sheetMargin = 15.dp
        val gapAboveSheet = 35.dp
    }

    /** 확장창 (그랩바를 끌어올렸을 때 뜨는 전체 화면) */
    object Expanded {
        /** 화면 좌우 여백 (Figma 41) */
        val horizontalPadding = 41.dp
        /** 상단 플로팅 버튼 줄 높이 */
        val topBarHeight = 50.dp
        /** 상단 버튼 좌우 여백 */
        val topBarMargin = 10.dp
        /** 정류장 확장창: 상단 버튼 줄 ↔ 유형 칩 (Figma 83 → 94) */
        val stopHeaderTop = 11.dp
        /** 정류장 확장창: 갱신 안내 ↔ 구분선 (Figma 166 → 173) */
        val stopDividerTop = 7.dp
        /** 노선 확장창: 상단 버튼 줄 ↔ 헤더 (Figma 83 → 90) */
        val routeHeaderTop = 7.dp
        /** 노선 확장창: 운행 대수 ↔ 구분선 (Figma 167 → 173) */
        val routeDividerTop = 6.dp
        /** 2열 그리드 왼쪽 여백 (Figma 50) */
        val gridPadding = 50.dp
        /** 2열 그리드 오른쪽 여백 (Figma 40 — 143 칸 두 개 + 36 사이) */
        val gridPaddingEnd = 40.dp
        /** 오른쪽 아래 새로고침 버튼 여백 */
        val fabMargin = 10.dp
        val fabBottom = 25.dp
    }

    /** 검색 화면 */
    object SearchScreen {
        // --- 결과 한 줄 (Figma: 구분선 간격 57 = 위 4 + 내용 50 + 아래 3) ---
        val rowHeight = 50.dp
        val rowPaddingTop = 4.dp
        val rowPaddingBottom = 3.dp
        /** 행 좌우 여백 (Figma 27) */
        val rowPaddingHorizontal = 27.dp

        // --- 정류장 행 ---
        /** 정류장 이름 (Figma 20, Medium) */
        val stopNameText = 22.sp
        /** 이름 윗 여백 (Figma 3) */
        val stopNameTop = 3.dp
        /** 이름 줄 ↔ 둘째 줄 (Figma: 이름 위 3 + 글자 20 → 28 이므로 5) */
        val stopSubGap = 3.dp
        /** 정류장 번호 (Figma 13, Medium, #848487) */
        val stopNoText = 13.sp
        /** 번호 칸 폭 — 유형 칩이 항상 같은 자리에서 시작하게 (Figma 30) */
        val stopNoWidth = 30.dp
        /** 번호 ↔ 칩, 칩 ↔ 칩 (Figma 36-30 = 6) */
        val chipGap = 6.dp

        // --- 노선 행 ---
        /** 뱃지 ↔ 오른쪽 (Figma 14) */
        val badgeGap = 14.dp
        /** 유형 칩 ↔ 기점·종점 (Figma 2) */
        val routeInfoGap = 0.dp
        /** 기점 → 종점 (Figma 12) */
        val routeInfoText = 12.sp

        // --- 아래 검색바 (Figma Simple lower bar: 368x48, 좌우 22, 아래 25) ---
        val barHeight = 48.dp
        val barCorner = 24.dp
        val barMarginHorizontal = 22.dp
        val barMarginBottom = 25.dp
        val barPaddingHorizontal = 18.dp
        val barText = 16.sp

        /** 목록이 검색바 뒤까지 이어지도록 두는 아래 여백 */
        val listBottomPadding = 96.dp
        /** 뒤로가기 버튼 아래부터 목록이 시작하는 지점 */
        val listTopPadding = 68.dp

        /** 글자를 친 뒤 이만큼 기다렸다가 검색한다 */
        const val typingDebounceMs = 180L
        /** 위아래 그라데이션 높이 (Figma Nav bar background 44) */
        val fadeHeight = 44.dp
        /** 검색 기록 줄의 지우기 아이콘 */
        val removeIconSize = 18.dp
    }

    /**
     * 즐겨찾기 목록 글자 (FavoritesList).
     * 접힘(떠 있는 카드) 값 → 펼침(전체 화면) 값으로 모핑 진행도에 따라 보간한다.
     * 펼침 상태의 정류장 줄은 검색창(SearchScreen)과 같은 크기를 쓴다.
     */
    object Favorites {
        /** 정류장 이름: 접힘 / 펼침(= 검색창 22sp) */
        val stopNameCollapsed = 22.sp
        val stopNameExpanded = SearchScreen.stopNameText
        /** 정류장 번호: 접힘 / 펼침(= 검색창 13sp) */
        val stopNoCollapsed = 14.sp
        val stopNoExpanded = SearchScreen.stopNoText
    }

    /** 시트 헤더 (정류장 이름 / 노선 번호 줄) */
    object Header {
        val stopNameText = 24.sp
        /** 정류장 이름 줄 높이 (Figma 텍스트 상자 28) */
        val stopNameLine = 28.sp
        val metaText = 12.sp
        /** 갱신 안내 줄 높이 (Figma 텍스트 상자 14) */
        val metaLine = 14.sp
        /** 정류장 이름 ↔ 갱신 안내 (Figma 86 → 91) */
        val metaTop = 5.dp

        val routeNoText = 24.sp
        /** 노선번호 줄 높이 (Figma 텍스트 상자 28) */
        val routeNoLine = 28.sp
        /** 유형 칩 ↔ 노선번호 (Figma 67 → 73) */
        val routeNoStart = 6.dp
        val infoText = 12.sp
        /** 정보 한 줄 높이 (Figma 14) */
        val infoLine = 14.sp
        /** 노선번호 줄 ↔ 기점→종점 (Figma 57 → 57) */
        val infoTop = 0.dp
        /** 기점→종점 ↔ 첫차·막차·배차 (Figma 71 → 75) */
        val scheduleTop = 4.dp
        /** 첫차 줄 ↔ 운행 중 N대 (Figma 89 → 92) */
        val runningTop = 3.dp


        /** 오른쪽 아이콘 */
        val iconSize = 24.dp
        /**
         * 노선 시트에서 새로고침 · 즐겨찾기가 놓이는 높이.
         * Figma: 헤더 위쪽에서 32 (정류장 시트의 아이콘과 같은 줄에 온다)
         */
        val actionsTop = 32.dp
    }

    /** 정류장 시트의 노선 한 칸 (2열 그리드) */
    object RouteRow {
        val width = 143.dp
        val height = 50.dp
        /** 열 사이 간격 */
        val columnGap = 36.dp
        /** 남은 시간 (9분) — Figma 20 */
        val timeText = 20.sp
        /** 운행 정보가 없을 때 대체 문구 */
        val idleText = 20.sp
        /** N전 · 다음 N분 — Figma 10 */
        val subText = 10.sp
        /** 두 줄의 글자 메트릭 사이에 별도 여백을 더하지 않는다. */
        val subGap = 0.dp
    }

    /** 노선 번호 원형 뱃지 */
    object Badge {
        val size = 38.dp
        /** 뱃지 ↔ 오른쪽 텍스트 */
        val gap = 14.dp
        val text = 13.sp
        /** 4자리 이상 노선 번호일 때 */
        val textSmall = 11.sp
        /** "서면5(서면100)" 의 괄호 줄 (Figma 6sp) */
        val textSub = 6.sp
    }

    /** 노선 유형 칩 (지선 / 간선 / 마을) — Figma 공통 */
    object TypeChip {
        val corner = 6.dp
        val paddingHorizontal = 8.dp
        val paddingVertical = 3.dp
        /** 헤더 칩 크기 (Figma 39x20) */
        val width = 39.dp
        val height = 20.dp
        /** 검색 결과처럼 작은 칩 (Figma 30x16) */
        val widthSmall = 30.dp
        val heightSmall = 16.dp
        /** 시트 헤더용 */
        val text = 12.sp
        /** 검색 결과 행처럼 작은 곳 */
        val textSmall = 10.sp
        /** 칩 사이 간격 */
        val gap = 6.dp
    }

    /** 노선 시트의 세로 타임라인 */
    object Timeline {
        /** 정류장 한 줄의 높이 (이 값이 곧 정류장 사이 간격) */
        val rowHeight = 53.dp

        /** 왼쪽 차량번호 칩이 놓이는 영역 폭 (Figma: 세로선 중심 68 - 기둥 반폭 14) */
        val chipColumnWidth = 54.dp
        /** 칩 ↔ 버스 원 (Figma 52 → 54) */
        val chipColumnEndPadding = 2.dp

        /** 세로선이 지나는 기둥 영역 폭 (선이 이 한가운데를 지난다) */
        val columnWidth = 28.dp
        /** 세로선 두께 */
        val lineWidth = 4.dp

        /** 일반 정류장 동그라미 */
        val dotSize = 12.dp
        val dotBorder = 2.dp
        /** 버스가 있는 정류장의 아이콘 원 (Figma 28) */
        val busDotSize = 28.dp
        val busIconSize = 16.dp

        /** 기둥 ↔ 정류장 이름 사이 간격 (Figma 82 → 103) */
        val textGap = 21.dp
        val nameText = 17.sp
        val infoText = 14.sp

        /**
         * 현재 정류장 오버레이가 시작되는 x (Figma: 노선 흐름 오른쪽부터 끝까지).
         * 세로선 오른쪽 모서리 = 칩 영역 + 기둥 반폭 + 선 반폭.
         */
        val currentOverlayStart = chipColumnWidth + columnWidth / 2 + lineWidth / 2

        /** 차량번호 칩 (Figma: 높이 13, r6, 11sp Bold, 글자 좌우 5) */
        val vehicleChipCorner = 6.dp
        val vehicleChipHeight = 13.dp
        val vehicleChipText = 11.sp
        val vehicleChipPaddingHorizontal = 5.dp

        /** 차량번호 칩 아래 차종 칩 (Figma: 높이 13, r6, 8sp Medium, 위 칩과 2 간격) */
        val modelChipHeight = 13.dp
        val modelChipText = 8.sp
        val modelChipGap = 2.dp
    }

    /**
     * 노선 시간표 (Figma 81:937 "노선세부 확장창 / 시간표 보기", 81:1684).
     * 폭 332 표를 가운데에 두고(좌우 40), 구분선 아래 34 부터 표끼리 27 간격으로 쌓는다.
     * 표 = 머리줄(높이 27, 위 모서리 10, 12sp Bold) + 본문(안쪽 5, 5칸, 칸 높이 18, 아래 모서리 10).
     * 칸은 바둑판처럼 한 칸 걸러 회색이다. 글자 12sp SemiBold.
     */
    object Timetable {
        val horizontalPadding = 40.dp
        /** 구분선 ↔ 첫 표 (Figma 174 → 208) */
        val topPadding = 34.dp
        val sectionGap = 27.dp
        val corner = 10.dp
        val headerHeight = 27.dp
        val headerText = 12.sp
        val bodyPadding = 5.dp
        const val columns = 5
        val cellHeight = 18.dp
        val cellText = 12.sp
        val noteText = 12.sp
        /** 목록 맨 아래 여백 (내비게이션 바 위) */
        val bottomPadding = 40.dp
        /** 노선 타임라인 마지막 정류장 ↔ [시간표 보기] 바 윗변 사이 여유 */
        val barClearance = 16.dp
    }

    /** 지도 오버레이 */
    object Map {
        /** 노선 경로선 두께 */
        val pathWidth = 6.dp
        /** 마커 크기 (dp 숫자 — 네이버 SDK 가 픽셀을 요구해서 Float 로 둔다) */
        /** 정류장 마커 (버스 아이콘 원) */
        val stopMarkerSize = 24f
        /** 노선 화면의 경유 정류장 점 */
        val routeStopDotSize = 16f
        /** 버스 마커의 원 지름 */
        val busDotSize = 24f
        /** 마커 옆 글자 크기 (sp) */
        val captionText = 11f
        /** 전체 경로를 화면에 맞출 때 여백 */
        val fitPadding = 48.dp
        /** 검색바가 덮는 위쪽 영역 (dp) — 카메라가 이만큼 피해서 잡힌다 */
        val contentPaddingTop = 96f
        /** 시트가 덮는 아래쪽 영역에 더할 여유 (dp) */
        val contentPaddingBottomExtra = 12f
        /** 정류장 하나만 볼 때 줌 레벨 (클수록 확대) */
        const val STOP_ZOOM = 16.0
        /** 정류장 마커가 보이기 시작하는 줌. 네이버 축척 50m 근처 */
        const val STOP_MARKER_MIN_ZOOM = 16.0
        /** 노선 경유 정류장 점이 보이기 시작하는 줌. 네이버 축척 250m 근처 */
        const val ROUTE_STOP_MIN_ZOOM = 14.0
        /** 이만큼(m) 움직였을 때만 주변 정류장을 다시 불러온다 */
        const val NEARBY_RELOAD_METERS = 300.0

        /** 유리 표면 뒤에 깔 지도 스냅샷을 몇 분의 1로 줄여 뜰지 (클수록 가볍고 더 뭉개짐) */
        const val backdropDownscale = 4
        /** 지도가 움직이는 동안 스냅샷을 뜨는 주기(ms) */
        const val backdropIntervalMs = 20L
        /** 움직임이 멈춘 뒤 타일이 채워지는 동안 스냅샷을 뜨는 주기(ms) */
        const val backdropIdleIntervalMs = 250L
        /** 지도 그림이 마지막으로 바뀐 뒤 이만큼(ms) 지나면 스냅샷을 멈춘다 (다음 변화 때 다시 뜬다) */
        const val backdropSettleMs = 2500L
    }

    /**
     * 애니메이션 곡선.
     * 처음엔 빠르게 움직이고 끝으로 갈수록 느려지는 One UI 식 감속 곡선.
     */
    object Motion {
        val easing = CubicBezierEasing(0.22f, 0.25f, 0f, 1f)
        /** 짧은 전환 (사라지기 · 나타나기) */
        const val fast = 130
        /** 보통 전환 (시트 늘었다 줄었다, 메뉴 슬라이드) */
        const val medium = 260
        /** 모핑 시트(즐겨찾기·정류장·노선)가 펼쳐지고 접히는 시간 */
        const val morph = 460
        /** 모핑 시트가 처음 떠오르는 시간 */
        const val appear = 280
        /** 검색창이 지도 위로 페이드 인/아웃 되는 시간 */
        const val searchFade = 300
    }

    /** 진행 표시기 등 자잘한 것 */
    object Misc {
        val spinnerSize = 22.dp
        val smallSpinnerSize = 20.dp
        val spinnerStroke = 2.dp
    }
}
