package com.cheon.ccbuswidget.data.local

import android.content.Context
import com.cheon.ccbuswidget.data.local.TimetableStore.key
import com.cheon.ccbuswidget.data.model.RouteLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 노선 시간표 한 장.
 * [sections] 는 "종점발(…)" / "기점발(…)" / "S노선" 처럼 제목이 붙은 묶음이고,
 * [notes] 는 표 아래에 흐린 글씨로 붙는 안내문이다.
 */
data class Timetable(
    val sections: List<TimetableSection>,
    val notes: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = sections.all { it.times.isEmpty() } && notes.isEmpty()
}

data class TimetableSection(val title: String, val times: List<String>)

/**
 * 시간표 DB.
 *
 * 시간표는 코드가 아니라 `app/src/main/assets/timetables/` 폴더의 텍스트 파일에서 읽는다.
 * 파일 하나가 노선 하나이고, 파일 이름이 노선 번호다. (예: `300.txt`, `서면5.txt`)
 * 공백 · 영문 대소문자 · "S-1"/"S1" 같은 차이는 무시하고 찾는다. `_` 로 시작하는 파일은 무시.
 * 파일 형식은 같은 폴더의 `README.txt` 참고.
 *
 * 파일을 고친 뒤에는 앱을 다시 빌드해야 반영된다 (assets 는 APK 안에 들어가기 때문).
 */
object TimetableStore {
    private const val DIR = "timetables"

    /** 노선 번호 → 읽어 둔 시간표 (없는 노선은 NONE 으로 기억해서 매번 파일을 찾지 않는다) */
    private val cache = ConcurrentHashMap<String, Timetable>()
    private val NONE = Timetable(emptyList())

    /** 정리한 이름([key]) → 실제 파일 이름 (확장자 뺀 것) */
    @Volatile
    private var fileNames: Map<String, String>? = null

    /** 이 노선의 시간표. 파일이 없으면 null */
    suspend fun load(context: Context, routeNo: String, routeId: String? = null): Timetable? =
        withContext(Dispatchers.IO) {
            val cacheKey = routeId ?: routeNo
            cache[cacheKey]?.let { return@withContext it.takeUnless { t -> t === NONE } }

            val assets = context.applicationContext.assets
            val names = fileNames ?: runCatching { assets.list(DIR)?.toList() }.getOrNull().orEmpty()
                .filter { it.endsWith(".txt") && !it.startsWith("_") }
                .associateBy({ key(it.removeSuffix(".txt")) }, { it.removeSuffix(".txt") })
                .also { fileNames = it }

            val file = candidates(routeNo, routeId).firstNotNullOfOrNull { names[key(it)] }
            val table = file?.let { name ->
                runCatching {
                    assets.open("$DIR/$name.txt").bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
                }.getOrNull()
            }
            cache[cacheKey] = table ?: NONE
            table
        }

    /**
     * 파일 이름으로 찾아볼 순서.
     * 1) 노선 ID (같은 번호가 여러 개일 때 구분용, 예: CCB250000001)
     * 2) 앱에 보이는 노선 번호 그대로 (예: 서면5(서면100))
     * 3) 공백을 뺀 번호
     * 4) 괄호 앞 주번호 (예: 서면5)
     */
    private fun candidates(routeNo: String, routeId: String?): List<String> {
        val no = routeNo.trim()
        return listOfNotNull(
            routeId?.trim()?.takeIf { it.isNotEmpty() },
            no,
            no.replace(" ", ""),
            RouteLabel.of(no).main
        ).filter { it.isNotEmpty() }.distinct()
    }

    private val latinDash = Regex("""^([A-Z]+)-""")

    /**
     * 파일 이름과 노선 번호를 비교할 때 쓰는 정리된 이름.
     * 공백 무시 · 영문 대소문자 무시 · 맨 앞 영문 뒤 하이픈 무시 ("S-1" = "S1" = "s1").
     * 숫자 사이 하이픈은 그대로 둔다 ("10-1" 과 "101" 은 다른 노선).
     */
    private fun key(name: String): String =
        name.filterNot { it.isWhitespace() }.uppercase().replace(latinDash, "$1")

    private val sectionLine = Regex("""^\[(.*)]$""")
    private val timeToken = Regex("""^(\d{1,2}):(\d{2})$""")

    /**
     * 텍스트를 시간표로 바꾼다.
     *
     * ```
     * # 주석 (무시)
     * [종점발(춘천시청별관)]
     * 06:40 07:00 07:20
     * 7:40, 08:00
     * > 표 아래 안내문
     * ```
     */
    internal fun parse(text: String): Timetable {
        val sections = mutableListOf<TimetableSection>()
        val notes = mutableListOf<String>()
        var title: String? = null
        var times = mutableListOf<String>()

        fun flush() {
            if (title != null || times.isNotEmpty()) {
                sections += TimetableSection(title.orEmpty(), times)
            }
            times = mutableListOf()
        }

        text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("#") -> Unit
                line.startsWith(">") -> notes += line.removePrefix(">").trim()
                else -> {
                    val header = sectionLine.matchEntire(line)
                    if (header != null) {
                        flush()
                        title = header.groupValues[1].trim()
                    } else {
                        line.split(',', ' ', '\t', '·')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { times += normalize(it) }
                    }
                }
            }
        }
        flush()
        return Timetable(sections, notes)
    }

    /** "6:40" → "06:40". 시각 모양이 아니면 적힌 그대로 둔다 (예: "06:40S") */
    private fun normalize(token: String): String {
        val m = timeToken.matchEntire(token) ?: return token
        return m.groupValues[1].padStart(2, '0') + ":" + m.groupValues[2]
    }
}
