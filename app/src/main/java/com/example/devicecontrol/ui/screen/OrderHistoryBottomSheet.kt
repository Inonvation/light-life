package com.example.devicecontrol.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devicecontrol.data.OrderHistoryItem
import com.example.devicecontrol.ui.theme.LogColors
import com.example.devicecontrol.ui.theme.Spacings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderHistoryBottomSheet(
    orders: List<OrderHistoryItem>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expandedIndex by remember { mutableStateOf(-1) }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "历史订单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDismiss) {
                    Text("关闭", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(Spacings.sm))

            if (orders.isEmpty()) {
                Text(
                    "暂无历史订单",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                )
            } else {
                val groupedOrders = remember(orders) { groupOrdersByDate(orders) }

                LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                    groupedOrders.forEach { (dateHeader, items) ->
                        item(key = "header_$dateHeader") {
                            OrderDateSectionHeader(dateHeader, items.size)
                        }
                        itemsIndexed(items, key = { _, item -> item.orderNo }) { _, item ->
                            val isExpanded = expandedIndex == orders.indexOf(item)
                            OrderCard(
                                item = item,
                                dateFormat = dateFormat,
                                isExpanded = isExpanded,
                                onClick = { expandedIndex = if (isExpanded) -1 else orders.indexOf(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderDateSectionHeader(date: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "  $count 笔",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Spacings.sm))
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun OrderCard(
    item: OrderHistoryItem,
    dateFormat: SimpleDateFormat,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon (always success since completed orders)
            Icon(
                Icons.Outlined.CheckCircleOutline,
                contentDescription = null,
                tint = LogColors.success,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(Spacings.sm))
            // Product name
            Text(
                text = item.goodsName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            // Price
            Text(
                text = "¥${item.originPrice}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
            // Expand icon
            Icon(
                imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (isExpanded) "折叠" else "展开详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        // Expanded content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Spacings.sm))

                // Detail rows
                DetailRow("时间", dateFormat.format(Date(item.completedAt)))
                DetailRow("订单号", item.orderNo)
                DetailRow("原价", item.originPrice)
                DetailRow("小票", item.ticketCost)
                DetailRow("积分", item.integralCost)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun groupOrdersByDate(orders: List<OrderHistoryItem>): List<Pair<String, List<OrderHistoryItem>>> {
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 86_400_000L

    val grouped = linkedMapOf<String, MutableList<OrderHistoryItem>>()
    for (order in orders) {
        val header = when {
            order.completedAt >= todayStart -> "今天"
            order.completedAt >= yesterdayStart -> "昨天"
            else -> {
                val cal = Calendar.getInstance().apply { timeInMillis = order.completedAt }
                "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
            }
        }
        grouped.getOrPut(header) { mutableListOf() }.add(order)
    }
    return grouped.entries.map { (key, value) -> key to value.toList() }
}
