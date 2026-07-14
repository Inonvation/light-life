const fs = require("fs");

// 1. AppUiState: add totalWaterCount field
const vmPath = "F:/qiekj-android/app/src/main/java/com/example/devicecontrol/ui/AppViewModel.kt";
let vm = fs.readFileSync(vmPath, "utf8");

// Add totalWaterCount after todayWaterAmount in AppUiState data class
vm = vm.replace(
    'val todayWaterAmount: String = "0.00",',
    'val todayWaterAmount: String = "0.00",\r\n    val totalWaterCount: Int = 0,'
);

// 2. AppViewModel: calculate total water count from orderHistory
// In refreshTodayWater, after calculating count and amount, also calculate total
vm = vm.replace(
    /val orders = repository\.orderHistory\(\)\.filter \{ it\.completedAt >= todayStart \}/,
    'val todayOrders = repository.orderHistory().filter { it.completedAt >= todayStart }\r\n        val allOrders = repository.orderHistory()'
);

vm = vm.replace(
    /val count = orders\.size/,
    'val count = todayOrders.size'
);

vm = vm.replace(
    /val amount = orders\.mapNotNull/,
    'val amount = todayOrders.mapNotNull'
);

vm = vm.replace(
    'todayWaterCount = count,\r\n            todayWaterAmount = String.format("%.2f", amount),',
    'todayWaterCount = count,\r\n            todayWaterAmount = String.format("%.2f", amount),\r\n            totalWaterCount = allOrders.size,'
);

// 3. Also reset totalWaterCount on logout
vm = vm.replace(
    'todayWaterCount = 0,\r\n            todayWaterAmount = "0.00",',
    'todayWaterCount = 0,\r\n            todayWaterAmount = "0.00",\r\n            totalWaterCount = 0,'
);

fs.writeFileSync(vmPath, vm, "utf8");
console.log("AppViewModel.kt updated");

// 4. ControlScreen: add total water count display
const csPath = "F:/qiekj-android/app/src/main/java/com/example/devicecontrol/ui/screen/ControlScreen.kt";
let cs = fs.readFileSync(csPath, "utf8");

// Add total water count row after the amount row
cs = cs.replace(
    'Text("\\u00a5${state.todayWaterAmount}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)',
    'Text("\\u00a5${state.todayWaterAmount}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)'
);

// Actually, let me find the exact closing of the amount row and add after it
const amountRowEnd = 'Text("\\u00a5${state.todayWaterAmount}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)\r\n                }';

const totalRow = 'Text("\\u00a5${state.todayWaterAmount}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)\r\n                }\r\n                Spacer(Modifier.height(4.dp))\r\n                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {\r\n                    Text("\\u603b\\u5171\\u5f00\\u6c34\\u6b21\\u6570", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)\r\n                    Text("${state.totalWaterCount} \\u6b21", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)\r\n                }';

cs = cs.replace(amountRowEnd, totalRow);

fs.writeFileSync(csPath, cs, "utf8");
console.log("ControlScreen.kt updated");
