const fs = require("fs");
const csPath = "F:/qiekj-android/app/src/main/java/com/example/devicecontrol/ui/screen/ControlScreen.kt";
let c = fs.readFileSync(csPath, "utf8");

const yen = "\u00a5";

const anchor = "Text(\"" + yen + "${state.todayWaterAmount}\", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)\r\n                }\r\n            }\r\n        }\r\n        Spacer(Modifier.height(30.dp))";

const replacement = "Text(\"" + yen + "${state.todayWaterAmount}\", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)\r\n                }\r\n                Spacer(Modifier.height(4.dp))\r\n                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {\r\n                    Text(\"\u603b\u5171\u5f00\u6c34\u6b21\u6570\", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)\r\n                    Text(\"${state.totalWaterCount} \u6b21\", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)\r\n                }\r\n            }\r\n        }\r\n        Spacer(Modifier.height(30.dp))";

const result = c.replace(anchor, replacement);
if (result === c) {
    console.log("WARNING: no match!");
} else {
    fs.writeFileSync(csPath, result, "utf8");
    console.log("ControlScreen.kt updated");
}
