package com.example.devicecontrol.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.devicecontrol.data.OrderHistoryItem

@Composable
fun OrderHistoryDialog(orders: List<OrderHistoryItem>, onDismiss: () -> Unit, onOpenOrder: (OrderHistoryItem) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("历史订单") }, shape = RoundedCornerShape(8.dp),
        text = {
            if (orders.isEmpty()) { Text("暂无历史订单", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else { LazyColumn { items(orders) { item -> OrderHistoryRow(item = item, onClick = { onOpenOrder(item) }) } } }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
fun OrderHistoryRow(item: OrderHistoryItem, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(item.goodsName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text("订单：${item.orderNo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("原价：${item.originPrice}  小票：${item.ticketCost}  积分：${item.integralCost}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp)); HorizontalDivider()
    }
}
