package com.cheon.ccbuswidget.feature.stopdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.ui.theme.Tokens

/**
 * Figma 67:1125 (324x57, 안쪽 6, 버튼 78x45). 지도·즐겨찾기만 선택 상태를 가진다.
 * 선택 칸은 반투명 #DDDDDD 배경 위에 같은 색 3dp 테두리를 한 번 더 겹쳐 가장자리가 조금 더 진하다.
 */
@Composable
internal fun MainFloatingToolbar(
    favoritesSelected: Boolean,
    onMap: () -> Unit,
    onFavorites: () -> Unit,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: ContentBackdrop? = null
) {
    val shape = RoundedCornerShape(Tokens.Toolbar.corner)
    val selectedOffset by animateDpAsState(
        if (favoritesSelected) Tokens.Toolbar.itemWidth else 0.dp,
        tween(Tokens.Motion.medium, easing = Tokens.Motion.easing), label = "선택 강조 위치"
    )
    val selectedColor = colorResource(R.color.navigation_selected).copy(alpha = Tokens.Toolbar.selectedAlpha)
    val itemShape = RoundedCornerShape(Tokens.Toolbar.itemCorner)
    NavigationGlassSurface(shape, glassColor(),
        modifier.width(Tokens.Toolbar.width).height(Tokens.Toolbar.height).glassShadow(shape), backdrop) {
        Box(Modifier.padding(start = Tokens.Toolbar.padding, top = Tokens.Toolbar.padding).offset(x = selectedOffset)
            .size(Tokens.Toolbar.itemWidth, Tokens.Toolbar.itemHeight)
            .background(selectedColor, itemShape)
            .border(Tokens.Toolbar.selectedBorder, selectedColor, itemShape))
        Row(Modifier.fillMaxSize().padding(Tokens.Toolbar.padding).selectableGroup()) {
            ToolbarItem("지도", if (favoritesSelected) R.drawable.ic_nav_map_outline else R.drawable.ic_nav_map_filled,
                !favoritesSelected, onMap)
            ToolbarItem("즐겨찾기", if (favoritesSelected) R.drawable.ic_nav_favorite_filled else R.drawable.ic_nav_favorite_outline,
                favoritesSelected, onFavorites)
            ToolbarItem("검색", R.drawable.ic_nav_search, null, onSearch)
            ToolbarItem("메뉴", R.drawable.ic_nav_menu, null, onMenu)
        }
    }
}

@Composable
private fun RowScope.ToolbarItem(label: String, icon: Int, selected: Boolean?, onClick: () -> Unit) {
    val interaction = if (selected == null) Modifier.clickable(role = Role.Button, onClick = onClick)
        else Modifier.selectable(selected, role = Role.Tab, onClick = onClick)
    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(Tokens.Toolbar.itemCorner)).then(interaction),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(Tokens.Toolbar.iconBoxHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(Tokens.Toolbar.iconSize), tint = colorResource(R.color.glass_on_surface))
        }
        Text(label, fontSize = Tokens.Toolbar.labelText, lineHeight = 13.sp, maxLines = 1,
            fontWeight = if (selected == true) FontWeight.Bold else FontWeight.Normal,
            color = colorResource(R.color.glass_on_surface))
    }
}

/**
 * 즐겨찾기 편집 중 툴바 자리에 뜨는 저장 · 취소 바 (Figma 70:1913).
 * 안쪽 좌우 24 · 위아래 14 안을 세 칸(저장 | 구분 | 취소)으로 똑같이 나눈 배치.
 * 구분 칸의 선은 Figma 에서 보이지 않아 그리지 않는다.
 */
@Composable
internal fun FavoritesEditActionBar(
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: ContentBackdrop? = null
) {
    val shape = RoundedCornerShape(Tokens.ActionBar.corner)
    val shadow = colorResource(R.color.glass_on_surface).copy(alpha = 0.15f)
    NavigationGlassSurface(shape, glassColor(),
        modifier.width(Tokens.ActionBar.width).height(Tokens.ActionBar.height)
            .shadow(Tokens.ActionBar.shadowElevation, shape, ambientColor = shadow, spotColor = shadow),
        backdrop) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            ActionBarOption("저장", onSave, Modifier.weight(1f))
            ActionBarOption("취소", onCancel, Modifier.weight(1f))
        }
    }
}

/**
 * 바를 반으로 나눈 한쪽 전체가 버튼이다.
 * Figma: 바깥 여백 24 + 세 칸(48씩) → 저장 글자 중심 x=48, 취소 x=144 로, 각 반쪽(96)의 정가운데와 같다.
 */
@Composable
private fun ActionBarOption(label: String, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxHeight().clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = Tokens.ActionBar.text, lineHeight = Tokens.ActionBar.text,
            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
            color = colorResource(R.color.glass_on_surface))
    }
}

/** Figma 67:3680. 화면 바닥의 툴바를 덮으며 나타나는 네 칸 메뉴. */
@Composable
internal fun BoxScope.MainMenuPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onPlaceholder: () -> Unit,
    backdrop: ContentBackdrop? = null
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(130))) {
        Box(Modifier.fillMaxSize().clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null, onClick = onDismiss
        ))
    }
    AnimatedVisibility(visible, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 8.dp),
        enter = fadeIn(tween(180)) + slideInVertically(tween(280, easing = Tokens.Motion.easing)) { it / 3 },
        exit = fadeOut(tween(130)) + slideOutVertically(tween(180)) { it / 3 }) {
        val shape = RoundedCornerShape(28.dp)
        NavigationGlassSurface(shape, glassColor(), Modifier.fillMaxWidth().height(129.dp).glassShadow(shape), backdrop) {
            Row(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                MenuItem("-", "더미 버튼 1", R.drawable.ic_nav_video, onPlaceholder)
                MenuItem("-", "더미 버튼 2", R.drawable.ic_nav_heart, onPlaceholder)
                MenuItem("계정설정", "계정 설정", R.drawable.ic_nav_account, onAccount)
                MenuItem("설정", "설정", R.drawable.ic_nav_settings, onSettings)
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, description: String, icon: Int, onClick: () -> Unit) {
    Column(Modifier.width(54.dp).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(54.dp).background(colorResource(R.color.navigation_menu_button), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), description, Modifier.size(24.dp), tint = colorResource(R.color.glass_on_surface))
        }
        Text(label, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 1, color = colorResource(R.color.glass_on_surface))
    }
}
