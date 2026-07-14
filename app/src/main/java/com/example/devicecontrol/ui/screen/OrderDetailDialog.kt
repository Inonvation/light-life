package com.example.devicecontrol.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.devicecontrol.data.UnlockResult
import com.example.devicecontrol.ui.theme.Spacings

@Composable
fun OrderDetailDialog(detail: UnlockResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("订单详情") }, shape = RoundedCornerShape(8.dp),
        text = {
            Column {
                DetailLine("订单号", detail.orderNo); DetailLine("订单 ID", detail.orderId); DetailLine("订单原价", detail.originPrice)
                DetailLine("花费小票", detail.ticketCost); DetailLine("积分抵扣", detail.integralCost)
                if (detail.otherPromotions.isNotEmpty()) {
                    Spacer(Modifier.height(Spacings.sm))
                    Text("其他优惠", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    detail.otherPromotions.forEach { p -> DetailLine(label = "类型 ${p.promotionType ?: "-"}", value = p.discountAmount ?: "-") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

@Composable
fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
