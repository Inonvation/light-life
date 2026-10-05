package com.inonvation.lightlife.data.qzxy

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 趣智蓝牙款设备的低功耗蓝牙（GATT）通道。
 *
 * 为什么必须走 GATT 而不是经典蓝牙 SPP：本校实测去连 `KLCXKJ-Water` 会触发系统配对
 * 并报「PIN 码或通行密钥不正确」，随后 socket 读取失败——设备的广播是低功耗蓝牙广播，
 * 不是串口透传设备。2026-09-27 在本校设备上读回的服务表，答案是 Microchip/ISSC 蓝牙透传服务：
 *
 * | 特征值 | 属性 | 用途 |
 * |---|---|---|
 * | `49535343-8841-43f4-a8d4-ecbe34729bb3` | 写 / 无应答写 | 往设备发数据 |
 * | `49535343-1e4d-4bd9-ba61-23c647249616` | 通知 | 收设备回包 |
 * | `49535343-aca3-481c-91ec-d85e28a60318` | 写 / 通知 | 流控（不写，主通道够用） |
 *
 * 设备另有一个 `0000ff00` 自定义服务（ff02 可写），写进去设备毫无反应——
 * 所以写入口认得出透传服务时就只用它，不逐个试。
 */
class QzxyBtClient private constructor(
    private val context: Context,
) {
    private var gatt: BluetoothGatt? = null

    /** 可写特征值；认得出透传写入口时只有它一个，否则按服务发现顺序全试 */
    private var writeCharacteristics: List<BluetoothGattCharacteristic> = emptyList()

    /** 协商到的 ATT MTU；没协商上就用默认 23（有效载荷 20 字节） */
    private var mtu = DEFAULT_MTU

    /** 设备主动上报的数据块，按到达顺序排队 */
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private var buffered = ByteArray(0)

    private var linkAlive = false
    private var connectedAddress: String? = null

    val isConnected: Boolean
        get() = linkAlive && gatt != null

    /** 当前连着的设备地址；未连接时为 null。开阀成功后记录它，结束使用时优先按它重连 */
    val address: String?
        get() = connectedAddress

    /**
     * 是否已经连着**指定**设备。判断能否复用链路必须用这个而不是 [isConnected]：
     * 连着另一台设备时 isConnected 同样是 true，复用会把指令发到别的设备上。
     */
    fun isConnectedTo(address: String): Boolean =
        isConnected && connectedAddress.equals(address, ignoreCase = true)

    /**
     * 连接设备。连接、MTU、服务发现任一失败都抛异常，异常文本直接显示给用户。
     * 已经连着同一台设备就直接复用：GATT 建链要一两秒，每次操作都重连纯属浪费。
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(address: String, timeoutMs: Long) = withContext(Dispatchers.IO) {
        if (linkAlive && connectedAddress.equals(address, ignoreCase = true)) return@withContext
        close()
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: throw IllegalStateException("本机没有蓝牙适配器")
        if (!adapter.isEnabled) throw IllegalStateException("请先打开系统蓝牙")

        val discovered = CompletableDeferred<Unit>()
        val device = adapter.getRemoteDevice(address)
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        linkAlive = true
                        // 先把 MTU 抬上去：查询帧加 # 与换行正好 20 字节，之后的 downData 更长。
                        // 协商失败也不要紧，写入按实际 MTU 分包
                        gatt.requestMtu(REQUESTED_MTU)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> if (!discovered.isCompleted) {
                        linkAlive = false
                        discovered.completeExceptionally(IllegalStateException("设备已断开（状态码 $status）"))
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    discovered.completeExceptionally(IllegalStateException("服务发现失败（状态码 $status）"))
                    return
                }
                val writable = gatt.services.asSequence()
                    .flatMap { it.characteristics.asSequence() }
                    .filter { characteristic ->
                        characteristic.properties and (
                            BluetoothGattCharacteristic.PROPERTY_WRITE or
                                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                            ) != 0
                    }
                    .toList()
                // 已知的透传写入口在就只用它：留多个候选的代价是「没回应」时把同一条
                // 命令往别的口重发一遍。别的厂商设备才退回逐个试
                writeCharacteristics = writable.filter { it.uuid == WRITE_UUID }.ifEmpty { writable }
                discovered.complete(Unit)
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) this@QzxyBtClient.mtu = mtu
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                incoming.trySend(value)
            }

            @Deprecated("API 33 以下走这个重载")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                incoming.trySend(value)
            }
        }

        try {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            try {
                withTimeout(timeoutMs) { discovered.await() }
            } catch (_: TimeoutCancellationException) {
                throw IllegalStateException("连接热水器超时，请靠近设备重试")
            }
            enableNotifications()
            connectedAddress = address
            Log.i(TAG, "已连接 $address，可写特征值 ${writeCharacteristics.size} 个")
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    /**
     * 打开回包口的通知。只开已知的那条（透传回包口，流控口其次）：
     * Android 的 GATT 同一时刻只允许一个操作在飞，连着写 CCCD 后面的会被静默丢掉。
     * 服务表里认不出时退回全开——没见过的设备也不能收不到回包。
     */
    @SuppressLint("MissingPermission")
    private fun enableNotifications() {
        val current = gatt ?: return
        val notifiable = current.services
            .flatMap { service -> service.characteristics }
            .filter { characteristic ->
                characteristic.properties and (
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                        BluetoothGattCharacteristic.PROPERTY_INDICATE
                    ) != 0
            }
        if (notifiable.isEmpty()) return
        val preferred = notifiable
            .filter { it.uuid == READ_UUID || it.uuid == FLOW_CONTROL_UUID }
            .sortedBy { if (it.uuid == READ_UUID) 0 else 1 }
        (preferred.ifEmpty { notifiable }).forEach { characteristic ->
            runCatching {
                current.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(CCCD_UUID)?.let { descriptor ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        current.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        current.writeDescriptor(descriptor)
                    }
                }
            }
        }
    }

    /**
     * 发一帧并等一帧回包，返回还原后的帧字节（仍需 [QzxyBtProtocol.decodeResponse] 判成败）。
     * 超时或设备全程无回应返回 null——后者多半是链路已废，这里主动断开，下次操作重新建链。
     */
    suspend fun request(frame: ByteArray, timeoutMs: Long): ByteArray? = withContext(Dispatchers.IO) {
        val current = gatt ?: return@withContext null
        if (writeCharacteristics.isEmpty()) {
            Log.w(TAG, "设备没有可写特征值")
            return@withContext null
        }
        val payload = QzxyBtProtocol.wrapFrame(frame)
        Log.i(TAG, "→ ${QzxyBtProtocol.bytesToHex(frame)}")
        var reply: ByteArray? = null
        for (characteristic in writeCharacteristics) {
            // 丢弃上一轮遗留的通知数据，免得把旧回包当成这次的应答
            while (incoming.tryReceive().isSuccess) { /* 消费即可 */ }
            buffered = ByteArray(0)
            try {
                writeChunked(current, characteristic, payload)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "写入失败：${e.message}")
                continue
            }
            reply = withTimeoutOrNull(timeoutMs) { readUntilLineFeed() }?.let { line ->
                QzxyBtProtocol.unwrapFrame(line) ?: line
            }
            if (reply != null) break
        }
        if (reply == null) {
            Log.i(TAG, "← 超时，设备没有回包")
            close()
        } else {
            Log.i(TAG, "← ${QzxyBtProtocol.bytesToHex(reply)}")
        }
        reply
    }

    /** 按协商到的 MTU 分包写入。默认 MTU 23 时每包 20 字节，长帧必须切开，否则写不进去 */
    @SuppressLint("MissingPermission")
    private suspend fun writeChunked(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
    ) {
        val chunkSize = (mtu - 3).coerceAtLeast(MIN_CHUNK_BYTES)
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + chunkSize, payload.size)
            write(gatt, characteristic, payload.copyOfRange(offset, end))
            offset = end
            if (offset < payload.size) delay(WRITE_GAP_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun write(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
    ) {
        // Android 的 GATT 同一时刻只允许一个操作在飞，排队失败时 writeCharacteristic
        // 返回 false 且不报错。刚连上时最常见：CCCD 写还没落地，第一条指令就被丢了，
        // 现象是「连接后的第一条命令超时」。所以重试几次，别把 false 当成功
        repeat(WRITE_ATTEMPTS) { attempt ->
            val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
            if (queued) return
            if (attempt < WRITE_ATTEMPTS - 1) delay(WRITE_RETRY_DELAY_MS)
        }
        throw IllegalStateException("GATT 忙，写入没有排上队")
    }

    /** 累积通知数据到出现换行为止；设备可能分好几包发 */
    private suspend fun readUntilLineFeed(): ByteArray? {
        while (true) {
            val chunk = incoming.receive()
            buffered += chunk
            val end = buffered.indexOfFirst { it == LINE_FEED }
            if (end >= 0) {
                val line = buffered.copyOfRange(0, end + 1)
                buffered = buffered.copyOfRange(end + 1, buffered.size)
                return line
            }
            if (buffered.size > MAX_RESPONSE_BYTES) {
                buffered = ByteArray(0)
                return null
            }
        }
    }

    /** 断开并释放。结束使用、重连前、异常分支都调它，不抛异常 */
    @SuppressLint("MissingPermission")
    fun close() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        linkAlive = false
        writeCharacteristics = emptyList()
        connectedAddress = null
        mtu = DEFAULT_MTU
        buffered = ByteArray(0)
    }

    companion object {
        private const val TAG = "QzxyBt"
        private const val DEFAULT_MTU = 23
        private const val REQUESTED_MTU = 247
        private const val MIN_CHUNK_BYTES = 20
        private const val WRITE_GAP_MS = 30L
        private const val WRITE_ATTEMPTS = 5
        private const val WRITE_RETRY_DELAY_MS = 40L
        private const val MAX_RESPONSE_BYTES = 512
        private const val LINE_FEED = 0x0A.toByte()

        /** Microchip/ISSC 蓝牙透传服务 */
        private val WRITE_UUID: UUID = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3")
        private val READ_UUID: UUID = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616")
        private val FLOW_CONTROL_UUID: UUID = UUID.fromString("49535343-aca3-481c-91ec-d85e28a60318")

        /** 客户端特征配置描述符，固定 UUID */
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * 依次尝试候选地址建链。扫描拿到的地址排最前（BLE 广播地址，实测 C0 开头）；
         * 服务端登记值（00 开头）连不上 BLE，只作后备。
         */
        suspend fun connect(context: Context, addressCandidates: List<String>, timeoutMs: Long): QzxyBtClient {
            var lastError: String = "无法连接热水器"
            for (address in addressCandidates.distinct()) {
                val client = QzxyBtClient(context)
                try {
                    client.connect(address, timeoutMs)
                    return client
                } catch (e: IllegalStateException) {
                    client.close()
                    lastError = e.message ?: lastError
                    Log.w(TAG, "连接 $address 失败：${e.message}")
                } catch (_: SecurityException) {
                    client.close()
                    lastError = "缺少蓝牙连接权限，请允许「附近的设备」权限后重试"
                    Log.w(TAG, "连接 $address 失败：权限不足")
                } catch (e: Exception) {
                    client.close()
                    lastError = "无法连接热水器，请确认在设备旁且设备已通电"
                    Log.w(TAG, "连接 $address 失败：${e.message}")
                }
            }
            throw IllegalStateException(lastError)
        }
    }
}
