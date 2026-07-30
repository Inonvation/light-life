package com.inonvation.lightlife.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizitonwose.calendar.compose.HeatMapCalendar
import com.kizitonwose.calendar.compose.heatmapcalendar.rememberHeatMapCalendarState
import com.kizitonwose.calendar.core.yearMonth
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// 扩展函数：获取星期几的短文本
private fun DayOfWeek.displayText(): String {
    return getDisplayName(TextStyle.SHORT, Locale.getDefault())
}

// 扩展函数：获取月份的短文本
private fun YearMonth.displayText(): String {
    return "${monthValue}月"
}

/**
 * 月度喝水热力图
 *
 * @param dailySummary 每日喝水量 Map (日期字符串如 "2026-7-21" -> 毫升)
 * @param dailyGoalMl 每日目标毫升，用于计算热力等级
 */
@Composable
fun MonthHeatMap(
    dailySummary: Map<String, Int>,
    dailyGoalMl: Int,
    modifier: Modifier = Modifier,
) {
    val endDate = remember { LocalDate.now() }
    val startDate = remember { endDate.minusMonths(5).withDayOfMonth(1) }

    // 将日期字符串转换为 LocalDate -> Int map
    val dataMap = remember(dailySummary) {
        dailySummary.mapNotNull { (key, ml) ->
            val parts = key.split("-")
            if (parts.size == 3) {
                val date = runCatching {
                    LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                }.getOrNull()
                date?.let { it to ml }
            } else null
        }.toMap()
    }

    // 热力等级颜色
    val colors = remember {
        listOf(
            Color(0xFFEBEDF0),  // 等级0：空白
            Color(0xFF9BE9A8),  // 等级1：<30% 目标
            Color(0xFF40C463),  // 等级2：30~60%
            Color(0xFF30A14E),  // 等级3：60~100%
            Color(0xFF216E3A),  // 等级4：>=100%
        )
    }

    fun levelOf(ml: Int): Int {
        if (ml <= 0) return 0
        val ratio = ml.toFloat() / dailyGoalMl
        return when {
            ratio < 0.3f -> 1
            ratio < 0.6f -> 2
            ratio < 1.0f -> 3
            else -> 4
        }
    }

    val state = rememberHeatMapCalendarState(
        startMonth = startDate.yearMonth,
        endMonth = endDate.yearMonth,
        firstVisibleMonth = endDate.yearMonth,
        firstDayOfWeek = DayOfWeek.MONDAY,
    )

    Column(modifier = modifier) {
        // 热力图
        HeatMapCalendar(
            state = state,
            dayContent = { day, week ->
                val ml = dataMap[day.date] ?: 0
                val level = levelOf(ml)

                when {
                    // 有效日期范围内（含今天）：按热力等级上色
                    day.date in startDate..endDate -> LevelBox(color = colors[level])
                    // 今天之后的未来日期（仍在 endMonth 内）：用浅灰空格补全，保证本周格数对齐
                    day.date > endDate -> LevelBox(color = colors[0])
                    // startDate 之前的月初补全：透明格占位
                    else -> LevelBox(color = Color.Transparent)
                }
            },
            weekHeader = { dayOfWeek ->
                // 显示星期标签（周二、周四、周六，对称排列）
                if (dayOfWeek == DayOfWeek.TUESDAY ||
                    dayOfWeek == DayOfWeek.THURSDAY ||
                    dayOfWeek == DayOfWeek.SATURDAY) {
                    WeekHeader(dayOfWeek = dayOfWeek)
                } else {
                    Spacer(modifier = Modifier.height(18.dp))
                }
            },
            monthHeader = { month ->
                // 月份标题
                if (month.weekDays.first().first().date <= endDate) {
                    MonthHeader(yearMonth = month.yearMonth)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // 图例
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "少",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            colors.forEach { color ->
                LevelBox(color = color)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "多",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LevelBox(color: Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color = color)
    )
}

@Composable
private fun WeekHeader(dayOfWeek: DayOfWeek) {
    Box(
        modifier = Modifier
            .height(18.dp)
            .padding(horizontal = 4.dp),
    ) {
        Text(
            text = dayOfWeek.displayText(),
            modifier = Modifier.align(Alignment.Center),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthHeader(yearMonth: YearMonth) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 1.dp, start = 2.dp),
    ) {
        Text(
            text = yearMonth.displayText(),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
