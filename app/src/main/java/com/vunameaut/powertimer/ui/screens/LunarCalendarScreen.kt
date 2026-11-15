package com.vunameaut.powertimer.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPink
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.ui.theme.AccentRed
import com.vunameaut.powertimer.widget.LunarCalendarAppWidgetProvider
import com.vunameaut.powertimer.widget.ToolsHubAppWidgetProvider
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

data class LunarDayFullInfo(
    val solarDay: Int,
    val solarMonth: Int,
    val solarYear: Int,
    val dayOfWeekIndex: Int, // 1 = CN, 2 = T2 ... 7 = T7 (theo Calendar)
    val dayOfWeekLabel: String,
    val lunarDay: Int,
    val lunarMonth: Int,
    val lunarYear: Int,
    val isLeapMonth: Boolean,
    val julianDay: Int,
    val yearCanChi: String,
    val monthCanChi: String,
    val dayCanChi: String,
    val firstHourCanChi: String,
    val zodiacAnimalEmoji: String,
    val isHoangDaoDay: Boolean,
    val starName: String,
    val solarTerm: String,
    val auspiciousHours: List<String>,
    val holidayName: String?
)

data class UpcomingLunarEvent(
    val title: String,
    val solarDateStr: String,
    val lunarDateStr: String,
    val daysRemaining: Int,
    val isSpecialHoliday: Boolean
)

/**
 * Thuật toán Lịch Âm - Dương Việt Nam chuẩn thiên văn (Hồ Ngọc Đức - Múi giờ UTC+7)
 * kèm bộ vẽ Lịch Tháng độ phân giải cao cho Tiện ích Màn hình chính (Home Screen Widget).
 */
object VietnameseLunarCalendar {

    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")
    private val ZODIAC_EMOJIS = arrayOf("🐭", "🐮", "🐯", "🐱", "🐲", "🐍", "🐴", "🐐", "🐵", "🐔", "🐶", "🐷")

    private val TIET_KHI = arrayOf(
        "Xuân Phân", "Thanh Minh", "Cốc Vũ", "Lập Hạ", "Tiểu Mãn", "Mang Chủng",
        "Hạ Chí", "Tiểu Thử", "Đại Thử", "Lập Thu", "Xử Thử", "Bạch Lộ",
        "Thu Phân", "Hàn Lộ", "Sương Giáng", "Lập Đông", "Tiểu Tuyết", "Đại Tuyết",
        "Đông Chí", "Tiểu Hàn", "Đại Hàn", "Lập Xuân", "Vũ Thủy", "Kinh Trập"
    )

    private val GIO_KHUNG = arrayOf(
        "23h-01h", "01h-03h", "03h-05h", "05h-07h", "07h-09h", "09h-11h",
        "11h-13h", "13h-15h", "15h-17h", "17h-19h", "19h-21h", "21h-23h"
    )

    // Bảng giờ Hoàng Đạo theo địa chi của ngày (1 = Hoàng Đạo, 0 = Hắc Đạo)
    private val GIO_HOANG_DAO_PATTERN = arrayOf(
        "110100101100", // Tý, Ngọ
        "001101001011", // Sửu, Mùi
        "110011010010", // Dần, Thân
        "101100110100", // Mão, Dậu
        "001011001101", // Thìn, Tuất
        "010010110011"  // Tỵ, Hợi
    )

    fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    fun jdToDate(jd: Int): Triple<Int, Int, Int> {
        val a: Int
        val b: Int
        val c: Int
        if (jd > 2299160) {
            a = jd + 32044
            b = (4 * a + 3) / 146097
            c = a - (b * 146097) / 4
        } else {
            b = 0
            c = jd + 32082
        }
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153
        val day = e - (153 * m + 2) / 5 + 1
        val month = m + 3 - 12 * (m / 10)
        val year = b * 100 + d - 4800 + m / 10
        return Triple(day, month, year)
    }

    private fun getNewMoonDay(k: Int, timeZone: Double = 7.0): Int {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = PI / 180.0
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c1 = c1 - 0.4068 * sin(mpr * dr) + 0.0161 * sin(dr * 2 * mpr)
        c1 -= 0.0004 * sin(dr * 3 * mpr)
        c1 = c1 + 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mpr))
        c1 = c1 - 0.0074 * sin(dr * (m - mpr)) + 0.0004 * sin(dr * (2 * f + m))
        c1 = c1 - 0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mpr))
        c1 += 0.0010 * sin(dr * (2 * f - mpr)) + 0.0005 * sin(dr * (2 * mpr + m))
        val deltat = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        val jdNew = jd1 + c1 - deltat
        return floor(jdNew + 0.5 + timeZone / 24.0).toInt()
    }

    private fun getSunLongitudeRadians(jdn: Double): Double {
        val t = (jdn - 2451545.0) / 36525.0
        val t2 = t * t
        val dr = PI / 180.0
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = l0 + dl
        l *= dr
        l -= PI * 2 * floor(l / (PI * 2))
        return l
    }

    private fun getSunLongitudeSector(dayNumber: Int, timeZone: Double = 7.0): Int {
        val l = getSunLongitudeRadians(dayNumber - 0.5 - timeZone / 24.0)
        return floor(l / PI * 6.0).toInt()
    }

    private fun getSolarTermIndex(dayNumber: Int, timeZone: Double = 7.0): Int {
        val l = getSunLongitudeRadians(dayNumber + 0.5 - timeZone / 24.0)
        return (floor(l / PI * 12.0).toInt() + 24) % 24
    }

    private fun getLunarMonth11(yy: Int, timeZone: Double = 7.0): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853).toInt()
        var nm = getNewMoonDay(k, timeZone)
        val sunLong = getSunLongitudeSector(nm, timeZone)
        if (sunLong >= 9) {
            nm = getNewMoonDay(k - 1, timeZone)
        }
        return nm
    }

    private fun getLeapMonthOffset(a11: Int, timeZone: Double = 7.0): Int {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var last: Int
        var i = 1
        var arc = getSunLongitudeSector(getNewMoonDay(k + i, timeZone), timeZone)
        do {
            last = arc
            i++
            arc = getSunLongitudeSector(getNewMoonDay(k + i, timeZone), timeZone)
        } while (arc != last && i < 14)
        return i - 1
    }

    /**
     * Chuyển đổi ngày Dương lịch sang đầy đủ thông tin Lịch Âm - Dương & Can Chi.
     */
    fun getFullDayInfo(dd: Int, mm: Int, yy: Int, timeZone: Double = 7.0): LunarDayFullInfo {
        val dayNumber = jdFromDate(dd, mm, yy)
        val k = floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = getNewMoonDay(k + 1, timeZone)
        if (monthStart > dayNumber) {
            monthStart = getNewMoonDay(k, timeZone)
        }
        var a11 = getLunarMonth11(yy, timeZone)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = getLunarMonth11(yy - 1, timeZone)
        } else {
            lunarYear = yy + 1
            b11 = getLunarMonth11(yy + 1, timeZone)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = ((monthStart - a11) / 29)
        var lunarLeap = false
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapMonthDiff = getLeapMonthOffset(a11, timeZone)
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10
                if (diff == leapMonthDiff) {
                    lunarLeap = true
                }
            }
        }
        if (lunarMonth > 12) {
            lunarMonth -= 12
        }
        if (lunarMonth >= 11 && diff < 4) {
            lunarYear -= 1
        }

        val cal = Calendar.getInstance().apply {
            set(yy, mm - 1, dd, 12, 0, 0)
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val dowLabel = when (dow) {
            Calendar.SUNDAY -> "Chủ Nhật"
            Calendar.MONDAY -> "Thứ Hai"
            Calendar.TUESDAY -> "Thứ Ba"
            Calendar.WEDNESDAY -> "Thứ Tư"
            Calendar.THURSDAY -> "Thứ Năm"
            Calendar.FRIDAY -> "Thứ Sáu"
            Calendar.SATURDAY -> "Thứ Bảy"
            else -> ""
        }

        val yearCan = CAN[(lunarYear + 6) % 10]
        val yearChiIdx = (lunarYear + 8) % 12
        val yearChi = CHI[yearChiIdx]
        val yearCanChi = "$yearCan $yearChi"
        val zodiacEmoji = ZODIAC_EMOJIS[yearChiIdx]

        val monthCan = CAN[(lunarYear * 12 + lunarMonth + 3) % 10]
        val monthChi = CHI[(lunarMonth + 1) % 12]
        val monthCanChi = "$monthCan $monthChi" + if (lunarLeap) " (Nhuận)" else ""

        val dayCan = CAN[(dayNumber + 9) % 10]
        val dayChiIdx = (dayNumber + 1) % 12
        val dayChi = CHI[dayChiIdx]
        val dayCanChi = "$dayCan $dayChi"

        val firstHourCan = CAN[(dayNumber - 1) * 2 % 10]
        val firstHourCanChi = "$firstHourCan Tý"

        val auspiciousHours = getAuspiciousHours(dayChiIdx)
        val (starName, isHoangDao) = getDayStarAndQuality(lunarMonth, dayChiIdx)
        val solarTerm = TIET_KHI[getSolarTermIndex(dayNumber, timeZone)]
        val holiday = getHolidayLabel(dd, mm, lunarDay, lunarMonth, lunarLeap)

        return LunarDayFullInfo(
            solarDay = dd,
            solarMonth = mm,
            solarYear = yy,
            dayOfWeekIndex = dow,
            dayOfWeekLabel = dowLabel,
            lunarDay = lunarDay,
            lunarMonth = lunarMonth,
            lunarYear = lunarYear,
            isLeapMonth = lunarLeap,
            julianDay = dayNumber,
            yearCanChi = yearCanChi,
            monthCanChi = monthCanChi,
            dayCanChi = dayCanChi,
            firstHourCanChi = firstHourCanChi,
            zodiacAnimalEmoji = zodiacEmoji,
            isHoangDaoDay = isHoangDao,
            starName = starName,
            solarTerm = solarTerm,
            auspiciousHours = auspiciousHours,
            holidayName = holiday
        )
    }

    private fun getAuspiciousHours(dayChiIdx: Int): List<String> {
        val pattern = GIO_HOANG_DAO_PATTERN[dayChiIdx % 6]
        val result = mutableListOf<String>()
        for (i in 0 until 12) {
            if (pattern[i] == '1') {
                result.add("${CHI[i]} (${GIO_KHUNG[i]})")
            }
        }
        return result
    }

    private fun getDayStarAndQuality(lunarMonth: Int, dayChiIdx: Int): Pair<String, Boolean> {
        // Khởi Thanh Long tại Tý (tháng 1,7), Dần (tháng 2,8), Thìn (tháng 3,9), Ngọ (tháng 4,10), Thân (tháng 5,11), Tuất (tháng 6,12)
        val startChi = ((lunarMonth - 1) % 6) * 2
        val offset = (dayChiIdx - startChi + 12) % 12
        return when (offset) {
            0 -> "Thanh Long Hoàng Đạo" to true
            1 -> "Minh Đường Hoàng Đạo" to true
            2 -> "Thiên Hình Hắc Đạo" to false
            3 -> "Chu Tước Hắc Đạo" to false
            4 -> "Kim Quỹ Hoàng Đạo" to true
            5 -> "Bảo Quang Hoàng Đạo" to true
            6 -> "Bạch Hổ Hắc Đạo" to false
            7 -> "Ngọc Đường Hoàng Đạo" to true
            8 -> "Thiên Lao Hắc Đạo" to false
            9 -> "Nguyên Vũ Hắc Đạo" to false
            10 -> "Tư Mệnh Hoàng Đạo" to true
            else -> "Câu Trận Hắc Đạo" to false
        }
    }

    private fun getHolidayLabel(
        solarDay: Int,
        solarMonth: Int,
        lunarDay: Int,
        lunarMonth: Int,
        isLeap: Boolean
    ): String? {
        if (!isLeap) {
            when (lunarMonth to lunarDay) {
                1 to 1 -> return "🧧 Mùng 1 Tết Nguyên Đán"
                1 to 2 -> return "🧧 Mùng 2 Tết Nguyên Đán"
                1 to 3 -> return "🧧 Mùng 3 Tết Nguyên Đán"
                1 to 15 -> return "🏮 Tết Nguyên Tiêu (Rằm tháng Giêng)"
                3 to 3 -> return "🍡 Tết Hàn Thực (3/3 ÂL)"
                3 to 10 -> return "🏛️ Giỗ Tổ Hùng Vương (10/3 ÂL)"
                4 to 15 -> return "🪷 Đại Lễ Phật Đản (15/4 ÂL)"
                5 to 5 -> return "🍃 Tết Đoan Ngọ (5/5 ÂL)"
                7 to 15 -> return "🌸 Lễ Vu Lan Báo Hiếu (Rằm tháng 7)"
                8 to 15 -> return "🥮 Tết Trung Thu (Rằm tháng 8)"
                12 to 23 -> return "🐟 Ông Công Ông Táo (23 tháng Chạp)"
                12 to 30 -> return "🧨 Giao Thừa (30 Tết)"
            }
        }
        when (solarMonth to solarDay) {
            1 to 1 -> return "🎉 Tết Dương Lịch (1/1)"
            2 to 14 -> return "💝 Lễ Tình Nhân Valentine (14/2)"
            3 to 8 -> return "💐 Quốc Tế Phụ Nữ (8/3)"
            4 to 30 -> return "🇻🇳 Ngày Giải Phóng Miền Nam (30/4)"
            5 to 1 -> return "🛠️ Quốc Tế Lao Động (1/5)"
            6 to 1 -> return "🎈 Quốc Tế Thiếu Nhi (1/6)"
            9 to 2 -> return "🇻🇳 Quốc Khánh Việt Nam (2/9)"
            10 to 20 -> return "🌷 Ngày Phụ Nữ Việt Nam (20/10)"
            11 to 20 -> return "📚 Ngày Nhà Giáo Việt Nam (20/11)"
            12 to 24, 12 to 25 -> return "🎄 Lễ Giáng Sinh (Noel)"
        }
        if (lunarDay == 1) return "🌙 Mùng 1 đầu tháng ($lunarDay/$lunarMonth ÂL)"
        if (lunarDay == 15) return "🌕 Ngày Rằm tháng $lunarMonth (15/$lunarMonth ÂL)"
        return null
    }

    /**
     * Lấy danh sách các ngày Rằm, Mùng 1 và Lễ Tết sắp tới trong 90 ngày tới.
     */
    fun getUpcomingEvents(maxCount: Int = 8): List<UpcomingLunarEvent> {
        val result = mutableListOf<UpcomingLunarEvent>()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)

        for (offset in 0..120) {
            val d = cal.get(Calendar.DAY_OF_MONTH)
            val m = cal.get(Calendar.MONTH) + 1
            val y = cal.get(Calendar.YEAR)
            val info = getFullDayInfo(d, m, y)
            val label = info.holidayName
            if (label != null) {
                val isSpecial = info.lunarDay != 1 && info.lunarDay != 15 ||
                    (info.lunarMonth == 1 && (info.lunarDay == 1 || info.lunarDay == 15)) ||
                    (info.lunarMonth == 7 && info.lunarDay == 15) ||
                    (info.lunarMonth == 8 && info.lunarDay == 15)
                result.add(
                    UpcomingLunarEvent(
                        title = label,
                        solarDateStr = String.format("%02d/%02d/%04d", d, m, y),
                        lunarDateStr = String.format("%02d/%02d ÂL", info.lunarDay, info.lunarMonth),
                        daysRemaining = offset,
                        isSpecialHoliday = isSpecial
                    )
                )
                if (result.size >= maxCount) break
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    /**
     * Vẽ bảng Lịch Tháng Âm - Dương độ phân giải cao (kiểu Google Calendar + Lịch Vạn Niên)
     * phục vụ hiển thị sắc nét trên Tiện ích Màn hình chính (AppWidget).
     */
    fun renderMonthWidgetBitmap(year: Int, month: Int): Bitmap {
        val width = 880
        val height = 540
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val todayCal = Calendar.getInstance()
        val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)
        val todayMonth = todayCal.get(Calendar.MONTH) + 1
        val todayYear = todayCal.get(Calendar.YEAR)
        val todayInfo = getFullDayInfo(todayDay, todayMonth, todayYear)

        val headerHeight = 46f
        val footerHeight = 62f
        val gridTop = headerHeight + 4f
        val gridBottom = height - footerHeight - 6f
        val gridHeight = gridBottom - gridTop
        val colWidth = width / 7f

        // 1. Vẽ tiêu đề 7 thứ trong tuần (T2 .. CN)
        val dowNames = arrayOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 23f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        for (col in 0..6) {
            headerPaint.color = when (col) {
                5 -> 0xFF38BDF8.toInt() // Thứ 7 xanh
                6 -> 0xFFF87171.toInt() // Chủ nhật đỏ
                else -> 0xFF94A3B8.toInt()
            }
            val cx = col * colWidth + colWidth / 2f
            canvas.drawText(dowNames[col], cx, 32f, headerPaint)
        }

        // Đường kẻ mảnh dưới hàng Thứ
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x26FFFFFF
            strokeWidth = 1.5f
        }
        canvas.drawLine(8f, headerHeight, width - 8f, headerHeight, dividerPaint)

        // 2. Tính toán các ngày trong tháng
        val monthCal = Calendar.getInstance().apply {
            set(year, month - 1, 1, 12, 0, 0)
        }
        val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDow = monthCal.get(Calendar.DAY_OF_WEEK) // 1 = CN, 2 = T2 .. 7 = T7
        val leadingEmpty = if (firstDow == Calendar.SUNDAY) 6 else (firstDow - Calendar.MONDAY)
        val totalCells = leadingEmpty + daysInMonth
        val numRows = if (totalCells > 35) 6 else 5
        val rowHeight = gridHeight / numRows

        val solarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 27f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val lunarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 17.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val cellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (day in 1..daysInMonth) {
            val cellIdx = leadingEmpty + (day - 1)
            val row = cellIdx / 7
            val col = cellIdx % 7

            val left = col * colWidth + 5f
            val top = gridTop + row * rowHeight + 3f
            val right = (col + 1) * colWidth - 5f
            val bottom = gridTop + (row + 1) * rowHeight - 3f
            val cx = (left + right) / 2f

            val info = getFullDayInfo(day, month, year)
            val isToday = (day == todayDay && month == todayMonth && year == todayYear)
            val isMung1OrRam = (info.lunarDay == 1 || info.lunarDay == 15)

            // Nền ô Hôm nay hoặc Mùng 1 / Rằm
            if (isToday) {
                cellBgPaint.style = Paint.Style.FILL
                cellBgPaint.color = 0xFF0EA5E9.toInt()
                canvas.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, cellBgPaint)
            } else if (isMung1OrRam) {
                cellBgPaint.style = Paint.Style.FILL
                cellBgPaint.color = 0x24F59E0B
                canvas.drawRoundRect(RectF(left, top, right, bottom), 14f, 14f, cellBgPaint)
                cellBgPaint.style = Paint.Style.STROKE
                cellBgPaint.strokeWidth = 1.5f
                cellBgPaint.color = 0x80F59E0B.toInt()
                canvas.drawRoundRect(RectF(left, top, right, bottom), 14f, 14f, cellBgPaint)
            }

            // Màu số ngày Dương lịch
            solarPaint.color = when {
                isToday -> 0xFFFFFFFF.toInt()
                col == 6 -> 0xFFF87171.toInt()
                col == 5 -> 0xFF38BDF8.toInt()
                else -> 0xFFF1F5F9.toInt()
            }
            val solarY = top + rowHeight * 0.44f
            canvas.drawText(day.toString(), cx, solarY, solarPaint)

            // Chữ ngày Âm lịch (hiện dd/mm nếu là Mùng 1 hoặc ngày đầu tháng Dương)
            val lunarLabel = if (info.lunarDay == 1 || day == 1) {
                "${info.lunarDay}/${info.lunarMonth}"
            } else {
                info.lunarDay.toString()
            }

            lunarPaint.color = when {
                isToday -> 0xFFE0F2FE.toInt()
                isMung1OrRam -> 0xFFFBBF24.toInt()
                else -> 0xFF94A3B8.toInt()
            }
            lunarPaint.typeface = if (isMung1OrRam || isToday) {
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val lunarY = top + rowHeight * 0.80f
            canvas.drawText(lunarLabel, cx, lunarY, lunarPaint)

            // Chấm xanh nhỏ báo Ngày Hoàng Đạo ở góc trên phải ô
            if (info.isHoangDaoDay) {
                dotPaint.color = if (isToday) 0xFFFFFFFF.toInt() else 0xFF4ADE80.toInt()
                canvas.drawCircle(right - 11f, top + 11f, 3.8f, dotPaint)
            }
        }

        // 3. Thanh chân trang hiển thị Can Chi & Giờ Hoàng Đạo của Hôm nay
        val footerRect = RectF(4f, height - footerHeight + 2f, width - 4f, height - 2f)
        val footerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x1EFFFFFF
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(footerRect, 16f, 16f, footerBgPaint)

        val footerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4ADE80.toInt()
            textSize = 19.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val canChiLine =
            "${todayInfo.zodiacAnimalEmoji} Ngày ${todayInfo.dayCanChi} • Tháng ${todayInfo.monthCanChi} • Năm ${todayInfo.yearCanChi} (${todayInfo.starName})"
        canvas.drawText(canChiLine, 18f, height - footerHeight + 26f, footerTextPaint)

        val hoursTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCBD5E1.toInt()
            textSize = 17.5f
            textAlign = Paint.Align.LEFT
        }
        val hoursShort = "✨ Giờ tốt hôm nay: " + todayInfo.auspiciousHours.take(4).joinToString(", ")
        canvas.drawText(hoursShort, 18f, height - 10f, hoursTextPaint)

        return bmp
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LunarCalendarTool(onExit: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onExit)

    val todayCal = remember { Calendar.getInstance() }
    val todayDay = remember { todayCal.get(Calendar.DAY_OF_MONTH) }
    val todayMonth = remember { todayCal.get(Calendar.MONTH) + 1 }
    val todayYear = remember { todayCal.get(Calendar.YEAR) }

    var viewingYear by remember { mutableIntStateOf(todayYear) }
    var viewingMonth by remember { mutableIntStateOf(todayMonth) }

    var selectedDay by remember { mutableIntStateOf(todayDay) }
    var selectedMonth by remember { mutableIntStateOf(todayMonth) }
    var selectedYear by remember { mutableIntStateOf(todayYear) }

    val selectedDayInfo = remember(selectedDay, selectedMonth, selectedYear) {
        VietnameseLunarCalendar.getFullDayInfo(selectedDay, selectedMonth, selectedYear)
    }

    val upcomingEvents = remember {
        VietnameseLunarCalendar.getUpcomingEvents(maxCount = 8)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Lịch Âm - Dương Vạn Niên",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Xem ngày tốt, Giờ Hoàng Đạo & Ghim Widget Lịch ra Màn hình chính",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Nút Ghim Tiện Ích Lịch Âm - Dương Ra Màn Hình Chính (Giống Google Calendar)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AccentCyan.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AccentCyan.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = AccentCyan
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tiện ích Lịch Âm - Dương ngoài Màn hình chính",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = AccentCyan
                        )
                        Text(
                            text = "Hiển thị nguyên bảng lịch tháng Âm - Dương + Giờ tốt giống Google Calendar ngay ngoài màn hình chính!",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            ToolsHubAppWidgetProvider.requestPinWidgetToHomeScreen(
                                context = context,
                                providerClass = LunarCalendarAppWidgetProvider::class.java,
                                label = "Lịch Âm - Dương (Tools Hub)"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ghim Lịch", fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                    }
                }
            }

            // 2. Tờ Lịch Bloc Chi Tiết Của Ngày Đang Chọn
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1E293B),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .border(
                        1.2.dp,
                        if (selectedDayInfo.isHoangDaoDay) AccentGreen.copy(alpha = 0.5f)
                        else Color.White.copy(alpha = 0.14f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Hàng trên: Dương lịch (trái) & Âm lịch (phải)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cột Dương Lịch
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "DƯƠNG LỊCH",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                            Text(
                                text = String.format("%02d", selectedDayInfo.solarDay),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = if (selectedDayInfo.dayOfWeekIndex == Calendar.SUNDAY) AccentRed else Color.White
                            )
                            Text(
                                text = "${selectedDayInfo.dayOfWeekLabel} • Tháng ${selectedDayInfo.solarMonth}/${selectedDayInfo.solarYear}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFCBD5E1)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(86.dp)
                                .background(Color.White.copy(alpha = 0.14f))
                        )

                        // Cột Âm Lịch
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "ÂM LỊCH ${selectedDayInfo.zodiacAnimalEmoji}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentAmber
                            )
                            Text(
                                text = String.format("%02d", selectedDayInfo.lunarDay),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = AccentAmber
                            )
                            Text(
                                text = "Tháng ${selectedDayInfo.lunarMonth}${if (selectedDayInfo.isLeapMonth) " (Nhuận)" else ""} • Năm ${selectedDayInfo.yearCanChi}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFDE68A),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Nhãn Ngày Lễ / Rằm / Mùng 1 nếu có
                    selectedDayInfo.holidayName?.let { holiday ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AccentRed.copy(alpha = 0.18f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AccentRed.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = holiday,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFFCA5A5),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Bảng Can Chi & Hoàng Đạo / Tiết Khí
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔮 Ngày ${selectedDayInfo.dayCanChi} • Tháng ${selectedDayInfo.monthCanChi}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = Color.White
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedDayInfo.isHoangDaoDay) {
                                        AccentGreen.copy(alpha = 0.2f)
                                    } else {
                                        Color.White.copy(alpha = 0.1f)
                                    }
                                ) {
                                    Text(
                                        text = if (selectedDayInfo.isHoangDaoDay) "🟢 ${selectedDayInfo.starName}"
                                        else "⚪ ${selectedDayInfo.starName}",
                                        color = if (selectedDayInfo.isHoangDaoDay) AccentGreen else Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Text(
                                text = "🌿 Tiết khí: ${selectedDayInfo.solarTerm}  •  Giờ đầu ngày: ${selectedDayInfo.firstHourCanChi}",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Danh sách 6 Khung Giờ Hoàng Đạo trong ngày
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Khung Giờ Hoàng Đạo (Giờ Tốt) trong ngày:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = AccentGreen
                            )
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            selectedDayInfo.auspiciousHours.forEach { hourChip ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentGreen.copy(alpha = 0.14f),
                                    modifier = Modifier.border(
                                        1.dp,
                                        AccentGreen.copy(alpha = 0.35f),
                                        RoundedCornerShape(8.dp)
                                    )
                                ) {
                                    Text(
                                        text = hourChip,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFBBF7D0),
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Bảng Lịch Tháng Âm - Dương Tương Tác (Giống Google Calendar + Lịch Vạn Niên)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Thanh chuyển tháng
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (viewingMonth == 1) {
                                    viewingMonth = 12
                                    viewingYear -= 1
                                } else {
                                    viewingMonth -= 1
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Tháng trước")
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "THÁNG $viewingMonth / $viewingYear",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = AccentCyan
                            )
                            Text(
                                text = "Chạm vào ngày bất kỳ để xem chi tiết Âm lịch & Giờ tốt",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (viewingMonth != todayMonth || viewingYear != todayYear ||
                                selectedDay != todayDay || selectedMonth != todayMonth || selectedYear != todayYear
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewingMonth = todayMonth
                                        viewingYear = todayYear
                                        selectedDay = todayDay
                                        selectedMonth = todayMonth
                                        selectedYear = todayYear
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Rounded.Today, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Hôm nay", fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (viewingMonth == 12) {
                                        viewingMonth = 1
                                        viewingYear += 1
                                    } else {
                                        viewingMonth += 1
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Tháng sau")
                            }
                        }
                    }

                    // Hàng Thứ (T2 .. CN)
                    val dowHeaders = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        dowHeaders.forEachIndexed { idx, title ->
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = when (idx) {
                                    5 -> AccentCyan
                                    6 -> AccentRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Lưới các ngày trong tháng
                    val monthGridData = remember(viewingYear, viewingMonth) {
                        val cal = Calendar.getInstance().apply {
                            set(viewingYear, viewingMonth - 1, 1, 12, 0, 0)
                        }
                        val daysCount = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val firstDow = cal.get(Calendar.DAY_OF_WEEK)
                        val leadingBlanks = if (firstDow == Calendar.SUNDAY) 6 else (firstDow - Calendar.MONDAY)
                        val daysList = (1..daysCount).map { d ->
                            VietnameseLunarCalendar.getFullDayInfo(d, viewingMonth, viewingYear)
                        }
                        leadingBlanks to daysList
                    }

                    val (leadingBlanks, daysList) = monthGridData
                    val totalSlots = leadingBlanks + daysList.size
                    val rowCount = (totalSlots + 6) / 7

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (row in 0 until rowCount) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (col in 0..6) {
                                    val slotIdx = row * 7 + col
                                    val dayIdx = slotIdx - leadingBlanks
                                    if (dayIdx in daysList.indices) {
                                        val info = daysList[dayIdx]
                                        val isToday = (info.solarDay == todayDay && info.solarMonth == todayMonth && info.solarYear == todayYear)
                                        val isSelected = (info.solarDay == selectedDay && info.solarMonth == selectedMonth && info.solarYear == selectedYear)
                                        val isMung1OrRam = (info.lunarDay == 1 || info.lunarDay == 15)

                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(0.92f)
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(
                                                    when {
                                                        isSelected -> AccentCyan.copy(alpha = 0.28f)
                                                        isToday -> AccentGreen.copy(alpha = 0.22f)
                                                        isMung1OrRam -> AccentAmber.copy(alpha = 0.12f)
                                                        else -> Color.White.copy(alpha = 0.03f)
                                                    }
                                                )
                                                .border(
                                                    width = if (isSelected || isToday) 1.5.dp else 0.8.dp,
                                                    color = when {
                                                        isSelected -> AccentCyan
                                                        isToday -> AccentGreen
                                                        isMung1OrRam -> AccentAmber.copy(alpha = 0.5f)
                                                        else -> Color.Transparent
                                                    },
                                                    shape = RoundedCornerShape(11.dp)
                                                )
                                                .clickable {
                                                    selectedDay = info.solarDay
                                                    selectedMonth = info.solarMonth
                                                    selectedYear = info.solarYear
                                                }
                                                .padding(2.dp)
                                        ) {
                                            // Chấm xanh góc trên phải cho Ngày Hoàng Đạo
                                            if (info.isHoangDaoDay) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(top = 3.dp, end = 3.dp)
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(AccentGreen)
                                                )
                                            }

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = info.solarDay.toString(),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.5.sp,
                                                    color = when {
                                                        isSelected -> AccentCyan
                                                        isToday -> AccentGreen
                                                        col == 6 -> AccentRed
                                                        col == 5 -> AccentCyan
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                val lunarSub = if (info.lunarDay == 1 || info.solarDay == 1) {
                                                    "${info.lunarDay}/${info.lunarMonth}"
                                                } else {
                                                    info.lunarDay.toString()
                                                }
                                                Text(
                                                    text = lunarSub,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isMung1OrRam) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isMung1OrRam) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Clip
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(0.92f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Chú thích ký hiệu dưới lịch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(AccentGreen))
                            Text("Ngày Hoàng Đạo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(AccentAmber))
                            Text("Mùng 1 / Ngày Rằm", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(AccentCyan))
                            Text("Đang chọn", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 4. Danh sách Ngày Rằm, Mùng 1 & Lễ Tết Sắp Tới
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.EventAvailable, contentDescription = null, tint = AccentAmber)
                        Text(
                            text = "Sắp tới: Rằm, Mùng 1 & Ngày Lễ Việt Nam",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AccentAmber
                        )
                    }

                    upcomingEvents.forEach { ev ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ev.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (ev.isSpecialHoliday) AccentPink else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Dương lịch: ${ev.solarDateStr}  •  Âm lịch: ${ev.lunarDateStr}",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (ev.daysRemaining == 0) AccentGreen.copy(alpha = 0.22f)
                                    else AccentPurple.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = if (ev.daysRemaining == 0) "Hôm nay" else "Còn ${ev.daysRemaining} ngày",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.5.sp,
                                        color = if (ev.daysRemaining == 0) AccentGreen else AccentCyan,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

