package com.cheon.ccbuswidget.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/**
 * 시스템 글꼴(삼성 One UI Sans) 찾기.
 *
 * 삼성 답변에 따르면 서드파티 앱은 기본적으로 Android 기본 글꼴(Roboto)로 그려지고,
 * One UI Sans 를 쓰려면 앱이 직접 지정해야 한다.
 * 기기마다 등록된 이름이 달라서 이름을 차례로 시도해 보고,
 * 그래도 못 찾으면 /system/fonts 에서 글꼴 파일을 직접 찾아 연다.
 * 삼성 기기가 아니면 못 찾고, 그때는 기본 글꼴을 그대로 쓴다.
 */
object OneUiFont {

    /** 삼성 기기에서 쓰이는(쓰였던) 글꼴 패밀리 이름들 */
    private val familyNames = listOf(
        "one-ui-sans",
        "OneUISans",
        "one_ui_sans",
        "SamsungOneUI",
        "SamsungOne",
        "SamsungSans",
        "sec",
        "sec-roboto-light"
    )

    /** /system/fonts 에서 찾을 파일 이름 조각 */
    private val fileHints = listOf("oneuisans", "oneui", "samsungone", "secroboto")

    /**
     * 찾아낸 글꼴 패밀리 이름.
     *
     * 위젯(RemoteViews)은 런처 프로세스에서 그려지는데,
     * Typeface 는 프로세스를 건너가지 못하고 '이름'만 건너간다.
     * 그래서 위젯에는 이 이름을 TypefaceSpan 으로 실어 보낸다.
     * (/system/fonts 에서 파일로 찾은 경우에는 이름이 없어 null)
     */
    var familyName: String? = null
        private set

    private val typeface: Typeface? by lazy { findTypeface() }

    /** 위젯 쪽에서 먼저 부르는 경우가 있어 찾기를 한 번 보장한다 */
    fun ensureLoaded() {
        typeface
    }

    /**
     * 굵기별로 진짜 글꼴을 뽑아 쓰는 글꼴 묶음.
     *
     * Typeface 하나만 넘기는 FontFamily 는 굵기를 아예 무시해서
     * Bold·Black 이 전부 보통 굵기로 나온다. 그래서 굵기마다
     * Typeface.create(기준글꼴, 굵기, 기울임) 로 새로 만들어 준다.
     */
    val fontFamily: FontFamily by lazy {
        val base = typeface ?: return@lazy FontFamily.Default
        FontFamily(
            SystemFont(base, FontWeight.Light),
            SystemFont(base, FontWeight.Normal),
            SystemFont(base, FontWeight.Medium),
            SystemFont(base, FontWeight.SemiBold),
            SystemFont(base, FontWeight.Bold),
            SystemFont(base, FontWeight.ExtraBold),
            SystemFont(base, FontWeight.Black)
        )
    }

    private fun findTypeface(): Typeface? {
        familyNames.forEach { name ->
            val tf = runCatching { Typeface.create(name, Typeface.NORMAL) }.getOrNull()
            // 등록되지 않은 이름이면 기본 글꼴이 그대로 돌아온다
            if (tf != null && tf != Typeface.DEFAULT) {
                familyName = name
                return tf
            }
        }
        return fromSystemFonts()
    }

    private fun fromSystemFonts(): Typeface? = runCatching {
        val file = File("/system/fonts").listFiles()
            ?.filter { f ->
                val n = f.name.lowercase()
                (n.endsWith(".ttf") || n.endsWith(".otf")) && fileHints.any { n.contains(it) }
            }
            // Regular 를 우선 (이름이 짧은 쪽이 대체로 기본 굵기다)
            ?.minByOrNull { it.name.length }
        file?.let { Typeface.createFromFile(it) }
    }.getOrNull()
}

/** 기준 글꼴에서 굵기 하나를 뽑아 쓰는 글꼴 */
private class SystemFont(
    val base: Typeface,
    override val weight: FontWeight,
    override val style: FontStyle = FontStyle.Normal
) : AndroidFont(FontLoadingStrategy.Blocking, Loader, FontVariation.Settings()) {

    object Loader : TypefaceLoader {
        override fun loadBlocking(context: Context, font: AndroidFont): Typeface? {
            val f = font as? SystemFont ?: return null
            val italic = f.style == FontStyle.Italic
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // 가변 글꼴이면 진짜 굵기를, 아니면 가장 가까운 굵기를 만들어 준다
                Typeface.create(f.base, f.weight.weight, italic)
            } else {
                Typeface.create(
                    f.base,
                    when {
                        f.weight.weight >= FontWeight.Bold.weight && italic -> Typeface.BOLD_ITALIC
                        f.weight.weight >= FontWeight.Bold.weight -> Typeface.BOLD
                        italic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                )
            }
        }

        override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? =
            loadBlocking(context, font)
    }
}
