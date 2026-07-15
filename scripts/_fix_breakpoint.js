const fs = require('fs');
let c = fs.readFileSync('F:/light-life/app/src/main/java/com/example/devicecontrol/data/PointsTaskRunner.kt', 'utf8');

// 1. Remove the "任务流程结束" shortcut, always parse individual markers
c = c.replace(
    '        if (logContent.isNullOrBlank()) return LogBreakpoint(false, false, false, 0, 0)\r\n        if (logContent.contains("任务流程结束")) return LogBreakpoint(false, false, false, 0, 0)',
    '        if (logContent.isNullOrBlank()) return LogBreakpoint(false, false, false, 0, 0)'
);

// 2. Add diagnostic info to "已读取本地日志" message
c = c.replace(
    '        val bp = parseLogBreakpoint(todayLog)\r\n        log("已读取本地日志")',
    '        val bp = parseLogBreakpoint(todayLog)\r\n        val logInfo = when {\r\n            todayLog == null -> "未发现日志"\r\n            bp.signInDone && bp.browseDone && bp.taskListDone && bp.appVideoCount >= 20 && bp.alipayVideoCount >= 50 -> "上次已完成，所有步骤跳过"\r\n            else -> "检测到已完成：${buildString { if (bp.signInDone) append(" 签到") }; ${buildString { if (bp.browseDone) append(" 浏览") } }; ${buildString { if (bp.taskListDone) append(" 任务") } }; ${buildString { if (bp.appVideoCount > 0) append(" APP广告${bp.appVideoCount}/20") } }; ${buildString { if (bp.alipayVideoCount > 0) append(" 支付宝广告${bp.alipayVideoCount}/50") } }}" }' [replace]'
);

// Actually that's too complex for a regex. Let me simplify.
c = c.replace(
    '        val bp = parseLogBreakpoint(todayLog)\r\n        log("已读取本地日志")',
    '        val bp = parseLogBreakpoint(todayLog)\r\n        log("已读取本地日志")'
);

// Let me just do step 1 for now
fs.writeFileSync('F:/light-life/app/src/main/java/com/example/devicecontrol/data/PointsTaskRunner.kt', c, 'utf8');
console.log('Done');
