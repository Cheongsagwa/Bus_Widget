package com.cheon.ccbuswidget.feature.stopdetail
//노선 시간표 (Figma 81:937)
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.data.local.Timetable
import com.cheon.ccbuswidget.data.local.TimetableSection
import com.cheon.ccbuswidget.ui.theme.Tokens

/**
 * 노선 세부 확장창에서 [시간표 보기]를 누르면 타임라인 자리에 뜨는 시간표.
 * 데이터는 assets/timetables/노선번호.txt ([com.cheon.ccbuswidget.data.local.TimetableStore]).
 */
@Composable
internal fun RouteTimetable(
    routeNo: String,
    timetable: Timetable?,
    loading: Boolean,
    fadeSurface: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth()) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = Tokens.Timetable.topPadding)
                    .size(Tokens.Misc.spinnerSize),
                strokeWidth = Tokens.Misc.spinnerStroke,
                color = colorResource(R.color.glass_on_surface)
            )

            timetable == null || timetable.isEmpty -> Column(
                Modifier.fillMaxWidth().padding(top = Tokens.Timetable.topPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "등록된 시간표가 없습니다",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 어느 파일을 채우면 되는지 알려 준다
                Text(
                    if (timetable == null) "assets/timetables/$routeNo.txt 파일을 만들어 주세요"
                    else "assets/timetables 의 $routeNo 파일에 시각을 적어 주세요",
                    fontSize = Tokens.Timetable.noteText,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Tokens.Timetable.horizontalPadding,
                        end = Tokens.Timetable.horizontalPadding,
                        top = Tokens.Timetable.topPadding,
                        bottom = nav + Tokens.Timetable.bottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Timetable.sectionGap)
                ) {
                    itemsIndexed(timetable.sections, key = { i, s -> "$i:${s.title}" }) { _, section ->
                        TimetableCard(section)
                    }
                    if (timetable.notes.isNotEmpty()) {
                        item(key = "notes") {
                            Column {
                                timetable.notes.forEach { note ->
                                    Text(
                                        note,
                                        fontSize = Tokens.Timetable.noteText,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                TopFade(visible = listState.canScrollBackward, surface = fadeSurface)
                BottomFade(visible = listState.canScrollForward, surface = fadeSurface)
            }
        }
    }
}

/**
 * 표 하나 (Figma 81:1684 · 85:1324).
 * 머리줄: 높이 32, 위 모서리 15, 15sp Bold.
 * 본문: 5% 배경 + 좌·우·아래 5dp #D9D9D9 테두리, 아래 모서리 15. 칸은 14sp SemiBold,
 *       시각 뒤의 표시("(편)" 등)는 8sp 로 작게. 두 번째 칸부터 한 칸 걸러 회색.
 */
@Composable
private fun TimetableCard(section: TimetableSection) {
    val corner = Tokens.Timetable.corner
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(Tokens.Timetable.headerHeight)
                .background(
                    colorResource(R.color.timetable_header),
                    RoundedCornerShape(topStart = corner, topEnd = corner)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                section.title,
                fontSize = Tokens.Timetable.headerText,
                lineHeight = Tokens.Timetable.headerText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val columns = Tokens.Timetable.columns
        val cellColor = colorResource(R.color.timetable_cell)
        val bodyColor = colorResource(R.color.timetable_body)
        val textColor = colorResource(R.color.timetable_text)
        val border = Tokens.Timetable.borderWidth
        Column(
            Modifier.fillMaxWidth()
                .drawBehind {
                    val b = border.toPx()
                    val r = corner.toPx()
                    val outer = Path().apply {
                        addRoundRect(RoundRect(0f, 0f, size.width, size.height,
                            bottomLeftCornerRadius = CornerRadius(r, r),
                            bottomRightCornerRadius = CornerRadius(r, r)))
                    }
                    val inner = Path().apply {
                        val ir = (r - b).coerceAtLeast(0f)
                        addRoundRect(RoundRect(b, 0f, size.width - b, size.height - b,
                            bottomLeftCornerRadius = CornerRadius(ir, ir),
                            bottomRightCornerRadius = CornerRadius(ir, ir)))
                    }
                    drawPath(inner, bodyColor)
                    // 테두리 = 바깥 모양 - 안쪽 모양 (위쪽은 머리줄과 붙어 있어 비운다)
                    drawPath(Path.combine(PathOperation.Difference, outer, inner), cellColor)
                }
                .padding(start = border, end = border, bottom = border)
        ) {
            // 시간이 하나도 없으면 빈 줄 하나만 두어 표 모양을 유지한다
            val rows = section.times.chunked(columns).ifEmpty { listOf(emptyList()) }
            rows.forEachIndexed { r, rowTimes ->
                Row(Modifier.fillMaxWidth().height(Tokens.Timetable.cellHeight)) {
                    for (c in 0 until columns) {
                        val time = rowTimes.getOrNull(c)
                        // Figma: 두 번째 칸부터 한 칸 걸러 회색 (바둑판). 빈 칸은 칠하지 않는다.
                        val filled = time != null && (r + c) % 2 == 1
                        Box(
                            Modifier.weight(1f).height(Tokens.Timetable.cellHeight)
                                .then(if (filled) Modifier.background(cellColor) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (time != null) {
                                Text(
                                    timeLabel(time),
                                    fontSize = Tokens.Timetable.cellText,
                                    lineHeight = Tokens.Timetable.cellText,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val timePrefix = Regex("""^\d{1,2}:\d{2}""")

/** "06:40(편)" → "06:40" 은 보통 크기, 뒤의 "(편)" 은 작게 (Figma: 14sp / 8sp) */
private fun timeLabel(token: String): AnnotatedString {
    val time = timePrefix.find(token)?.value ?: return AnnotatedString(token)
    return buildAnnotatedString {
        append(time)
        val suffix = token.substring(time.length)
        if (suffix.isNotEmpty()) {
            withStyle(SpanStyle(fontSize = Tokens.Timetable.cellSuffixText)) { append(suffix) }
        }
    }
}
