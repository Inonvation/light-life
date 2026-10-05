package com.inonvation.lightlife.data.qzxy

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context

/**
 * 热水器 BLE 扫描器。趣智热水器通过蓝牙广播设备名（含 KLCXKJ-Water），
 * 扫到 MAC 地址后调 /device/info/mac 换取设备详情。
 *
 * 过滤在回调里做"包含"匹配而非 ScanFilter 精确匹配，
 * 因为广播名可能带后缀（如 KLCXKJ-Water-XXXX）。
 */
class QzxyBluetoothScanner(context: Context) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private var callback: ScanCallback? = null

    val isSupported: Boolean
        get() = bluetoothManager?.adapter?.bluetoothLeScanner != null

    fun start(onFound: (QzxyNearbyDevice) -> Unit, onError: (String) -> Unit) {
        if (callback != null) return
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            onError("蓝牙不可用，请检查手机蓝牙是否开启")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val cb = object : ScanCallback() {
            // 权限由调用方的权限流程先行授予；这里已用 runCatching 兜底 SecurityException，
            // 回调里再逐项 checkPermission 只会让代码更难读
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // 广播名取自广播包，不需要 BLUETOOTH_CONNECT；系统缓存名才需要
                val name = result.scanRecord?.deviceName
                    ?: runCatching { result.device?.name }.getOrNull()
                    ?: return
                if (!name.contains(QzxyApiConfig.BLE_NAME_FILTER, ignoreCase = true)) return
                val mac = result.device?.address ?: return
                onFound(QzxyNearbyDevice(mac = mac, name = name, rssi = result.rssi))
            }

            override fun onScanFailed(errorCode: Int) {
                callback = null
                onError(
                    when (errorCode) {
                        SCAN_FAILED_ALREADY_STARTED -> "蓝牙扫描已在进行中"
                        SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "蓝牙扫描注册失败，请重试"
                        SCAN_FAILED_INTERNAL_ERROR -> "蓝牙内部错误，请重试"
                        else -> "蓝牙扫描失败（错误码 $errorCode）"
                    }
                )
            }
        }
        callback = cb
        try {
            scanner.startScan(null, settings, cb)
        } catch (e: SecurityException) {
            callback = null
            onError("缺少蓝牙权限，无法扫描")
        }
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        try {
            bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: SecurityException) {
            // 权限已回收，扫描会随进程结束
        }
    }
}
