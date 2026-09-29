package com.cheon.ccbuswidget.ui.oneui
// 설정 · 계정 화면용 One UI 스타일 부품 (앱 지도 화면과 같은 색 · 버튼 · 아이콘을 쓴다)
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.feature.stopdetail.RoundFloatingButton
import com.cheon.ccbuswidget.ui.theme.Tokens

/** One UI 설정 화면 치수 */
private object OneUi {
    val sidePadding = 12.dp
    val cardCorner = 26.dp
    val rowPaddingH = 20.dp
    val rowPaddingV = 15.dp
    val rowMinHeight = 60.dp
    val iconSize = 24.dp
    val iconBox = 40.dp
    val titleSpace = 170.dp
    val largeTitle = 32.sp
    val smallTitle = 20.sp
    val groupLabel = 14.sp
    val rowTitle = 17.sp
    val rowSummary = 14.sp
}

/**
 * One UI 모양의 화면 틀 — 위쪽의 큰 제목(스크롤하면 작은 제목으로 바뀜) + 떠 있는 뒤로 버튼.
 * 뒤로 버튼은 지도 화면 · 확장창의 떠 있는 버튼과 같은 자리 · 모양이다.
 */
@Composable
fun OneUiScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    // 큰 제목이 거의 다 올라가면 위쪽 줄에 작은 제목이 나타난다
    val smallTitleAlpha by remember {
        derivedStateOf {
            val start = with(density) { (OneUi.titleSpace - 70.dp).toPx() }
            val end = with(density) { OneUi.titleSpace.toPx() }
            ((scroll.value - start) / (end - start)).coerceIn(0f, 1f)
        }
    }
    val surface = colorResource(R.color.expanded_surface)
    Box(Modifier.fillMaxSize().background(surface)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(top = status, bottom = nav + 24.dp)
        ) {
            // 큰 제목 (One UI: 화면 위쪽 여백을 크게 두고 제목을 아래쪽에)
            Box(
                Modifier.fillMaxWidth().height(OneUi.titleSpace)
                    .alpha(1f - smallTitleAlpha),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    title,
                    modifier = Modifier.padding(start = 28.dp, bottom = 24.dp),
                    fontSize = OneUi.largeTitle,
                    lineHeight = OneUi.largeTitle,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.glass_on_surface)
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = OneUi.sidePadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }

        // 위쪽 줄: 뒤로 버튼 + (스크롤하면) 작은 제목. 뒤로 가는 목록이 비치지 않게 배경을 깐다
        Box(
            Modifier.fillMaxWidth()
                .background(surface.copy(alpha = smallTitleAlpha))
                .padding(top = status + Tokens.Glass.topBarTop, bottom = Tokens.Glass.topBarTop)
                .height(Tokens.Glass.buttonSize)
        ) {
            RoundFloatingButton(
                iconRes = R.drawable.ic_back,
                description = "뒤로",
                modifier = Modifier.align(Alignment.CenterStart).padding(start = Tokens.Glass.buttonMargin),
                onClick = onBack
            )
            Text(
                title,
                modifier = Modifier.align(Alignment.Center).alpha(smallTitleAlpha),
                fontSize = OneUi.smallTitle,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.glass_on_surface),
                maxLines = 1
            )
        }
    }
}

/** 카드 위의 작은 묶음 이름 (One UI 의 카테고리 제목) */
@Composable
fun OneUiGroupLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp),
        fontSize = OneUi.groupLabel,
        fontWeight = FontWeight.SemiBold,
        color = colorResource(R.color.search_secondary)
    )
}

/** 모서리가 둥근 흰 카드 (확장창의 떠 있는 버튼과 같은 색) */
@Composable
fun OneUiCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(OneUi.cardCorner))
            .background(colorResource(R.color.expanded_button)),
        content = content
    )
}

/** 카드 안 줄 사이 구분선 (글자 시작 위치부터) */
@Composable
fun OneUiDivider(inset: Boolean = false) {
    Box(
        Modifier.fillMaxWidth()
            .padding(start = if (inset) OneUi.rowPaddingH + OneUi.iconBox + 14.dp else OneUi.rowPaddingH,
                end = OneUi.rowPaddingH)
            .height(1.dp)
            .background(colorResource(R.color.divider))
    )
}

/**
 * 카드 안 한 줄 — (아이콘) 제목 / 요약.
 * [titleColor] 로 위험한 동작(빨강) · 강조(파랑)를 나타낸다.
 */
@Composable
fun OneUiRow(
    title: String,
    summary: String? = null,
    iconRes: Int? = null,
    iconTint: Color = colorResource(R.color.glass_on_surface),
    titleColor: Color = colorResource(R.color.glass_on_surface),
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = OneUi.rowMinHeight)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled) { onClick() } else Modifier)
            .padding(horizontal = OneUi.rowPaddingH, vertical = OneUi.rowPaddingV)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Box(
                Modifier.size(OneUi.iconBox)
                    .background(colorResource(R.color.expanded_surface), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(iconRes), null, tint = iconTint, modifier = Modifier.size(OneUi.iconSize))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = OneUi.rowTitle, lineHeight = 22.sp, color = titleColor)
            if (!summary.isNullOrBlank()) {
                Text(
                    summary,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = OneUi.rowSummary,
                    lineHeight = 19.sp,
                    color = colorResource(R.color.search_secondary)
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** 카드 안 설명 글 (도움말) */
@Composable
fun OneUiText(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = OneUi.rowPaddingH, vertical = 18.dp)) {
        Text(title, fontSize = OneUi.rowTitle, fontWeight = FontWeight.SemiBold,
            color = colorResource(R.color.glass_on_surface))
        Text(
            body,
            modifier = Modifier.padding(top = 8.dp),
            fontSize = OneUi.rowSummary,
            lineHeight = 21.sp,
            color = colorResource(R.color.search_secondary)
        )
    }
}

/** 카드 안 입력칸 — 위에 이름, 아래에 둥근 회색 칸 (One UI 편집 칸) */
@Composable
fun OneUiTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true
) {
    val onSurface = colorResource(R.color.glass_on_surface)
    Column(Modifier.fillMaxWidth().padding(horizontal = OneUi.rowPaddingH, vertical = 12.dp)) {
        Text(label, fontSize = OneUi.rowSummary, fontWeight = FontWeight.SemiBold,
            color = colorResource(R.color.search_secondary))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 4,
            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, lineHeight = 21.sp, color = onSurface),
            cursorBrush = SolidColor(colorResource(R.color.app_accent)),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorResource(R.color.expanded_surface))
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, fontSize = 16.sp, color = colorResource(R.color.search_secondary),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            }
        )
    }
}

/** 알약 모양 버튼 (One UI 의 큰 버튼). [filled] 가 아니면 카드색 바탕에 강조색 글자 */
@Composable
fun OneUiButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    filled: Boolean = true,
    color: Color = colorResource(R.color.app_accent),
    contentColor: Color = Color.White
) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (filled) color else colorResource(R.color.expanded_button))
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) contentColor else color,
            textAlign = TextAlign.Center
        )
    }
}

/** 화면 맨 아래 작은 안내 글 */
@Composable
fun OneUiFootnote(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = colorResource(R.color.search_secondary)
    )
}

/** 켜고 끄는 줄 (One UI 스위치 — 켜지면 강조색) */
@Composable
fun OneUiSwitchRow(title: String, summary: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val accent = colorResource(R.color.app_accent)
    OneUiRow(
        title = title,
        summary = summary,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accent,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = colorResource(R.color.expanded_surface),
                    uncheckedBorderColor = colorResource(R.color.divider)
                )
            )
        }
    )
}

/** 값을 밀어서 고르는 줄 — 제목 오른쪽에 지금 값, 아래에 막대 */
@Composable
fun OneUiSliderRow(
    title: String,
    valueText: String,
    summary: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    val accent = colorResource(R.color.app_accent)
    Column(Modifier.fillMaxWidth().padding(horizontal = OneUi.rowPaddingH, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), fontSize = OneUi.rowTitle,
                color = colorResource(R.color.glass_on_surface))
            Text(valueText, fontSize = OneUi.rowSummary, fontWeight = FontWeight.SemiBold, color = accent)
        }
        if (!summary.isNullOrBlank()) {
            Text(summary, modifier = Modifier.padding(top = 2.dp), fontSize = OneUi.rowSummary,
                lineHeight = 19.sp, color = colorResource(R.color.search_secondary))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = colorResource(R.color.expanded_surface),
                activeTickColor = Color.White.copy(alpha = 0.6f),
                inactiveTickColor = colorResource(R.color.divider)
            )
        )
    }
}

/** 고를 수 있는 알약 (노선 번호 고르기 등). 고르면 [selectedColor] 로 채운다 */
@Composable
fun OneUiChip(text: String, selected: Boolean, selectedColor: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) selectedColor else colorResource(R.color.expanded_surface))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else colorResource(R.color.glass_on_surface),
            maxLines = 1
        )
    }
}
