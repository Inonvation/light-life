package com.inonvation.lightlife.data.qzxy

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 趣智蓝牙款设备帧协议（凯路创新水控，逆向自公开资料；本校真机闭环验证过的版本）。
 *
 * 协议来源与验证记录见 docs/qzxy-bt-protocol.md。帧结构：
 *
 * ```
 * 下标 0      包头 0x60
 * 下标 1..2   长度，大端，值 = 数据体长度 + 3
 * 下标 3      固定 0x80
 * 下标 4      功能码
 * 下标 5      固定 0x00
 * 下标 6..    数据体
 * 倒数第 2    校验和 = (数据体各字节之和 + 0x80 + 功能码 + 0x00) & 0xFF
 * 最后 1      结束码 0x16
 * ```
 *
 * 写入设备前过一层文本包装：`#` + 帧的小写十六进制 + `\n`，整串按 ASCII 字节发出；
 * 设备回包同格式，[unwrapFrame] 负责还原。
 *
 * 可信度锚点：唯一实测样本是查询设备命令 `60000480230000a316`，单元测试钉的是
 * 「与该样本逐字节一致」；其余偏移由官方 iOS SDK 反汇编与看雪反编译双源印证，
 * 并在本校设备上跑通（开阀出水、结算 ¥0.04、清除回空闲）。
 */
object QzxyBtProtocol {

    // ── 功能码（帧下标 4）──

    /** 查询设备信息，响应带项目号、设备号、随机数、设备状态 */
    const val CODE_QUERY_DEVICE = 0x23

    /** 下发费率数据，即开阀：数据体就是服务端返回的 downData */
    const val CODE_DOWN_RATE = 0x21

    /** 结束费率，即停阀 */
    const val CODE_END_RATE = 0x22

    /** 采集消费数据（结束使用时取 xfData 上传结算） */
    const val CODE_COLLECT = 0x85

    /** 清除已采集的消费数据 */
    const val CODE_CLEAR = 0x86

    const val HEADER = 0x60
    const val FIXED_AT_3 = 0x80
    const val FIXED_AT_5 = 0x00
    const val FOOTER = 0x16

    /** 长度字段相对数据体长度的偏移：数据体 + 下标 3、4、5 三字节 */
    private const val LENGTH_BIAS = 3

    /** 包头(1) + 长度(2) + 固定(3) 共 6 字节在数据体之前，校验和与结束码共 2 字节在其后 */
    private const val BODY_OFFSET = 6
    private const val TAIL_SIZE = 2
    const val MIN_FRAME_SIZE = BODY_OFFSET + TAIL_SIZE

    private const val WRAP_PREFIX = '#'
    private const val WRAP_SUFFIX = '\n'

    // ── 帧编解码 ──

    /** 组装一帧。[payload] 缺省为单字节 0x00，即查询类命令的空参数 */
    fun encodeFrame(functionCode: Int, payload: ByteArray = byteArrayOf(0x00)): ByteArray {
        val length = payload.size + LENGTH_BIAS
        val out = ByteArray(BODY_OFFSET + payload.size + TAIL_SIZE)
        out[0] = HEADER.toByte()
        out[1] = ((length shr 8) and 0xFF).toByte()
        out[2] = (length and 0xFF).toByte()
        out[3] = FIXED_AT_3.toByte()
        out[4] = (functionCode and 0xFF).toByte()
        out[5] = FIXED_AT_5.toByte()
        payload.copyInto(out, BODY_OFFSET)

        var sum = FIXED_AT_3 + (functionCode and 0xFF) + FIXED_AT_5
        payload.forEach { sum += it.toInt() and 0xFF }
        out[BODY_OFFSET + payload.size] = (sum and 0xFF).toByte()
        out[BODY_OFFSET + payload.size + 1] = FOOTER.toByte()
        return out
    }

    /** 解析一帧，校验包头、长度、校验和与结束码，全过才返回 (功能码, 数据体)，否则 null */
    fun parseFrame(frame: ByteArray): Pair<Int, ByteArray>? {
        if (frame.size < MIN_FRAME_SIZE) return null
        if ((frame[0].toInt() and 0xFF) != HEADER) return null
        if ((frame[frame.size - 1].toInt() and 0xFF) != FOOTER) return null

        val length = ((frame[1].toInt() and 0xFF) shl 8) or (frame[2].toInt() and 0xFF)
        val payloadSize = length - LENGTH_BIAS
        if (payloadSize < 0) return null
        if (BODY_OFFSET + payloadSize + TAIL_SIZE != frame.size) return null

        val payload = frame.copyOfRange(BODY_OFFSET, BODY_OFFSET + payloadSize)
        var sum = (frame[3].toInt() and 0xFF) + (frame[4].toInt() and 0xFF) + (frame[5].toInt() and 0xFF)
        payload.forEach { sum += it.toInt() and 0xFF }
        if ((sum and 0xFF) != (frame[BODY_OFFSET + payloadSize].toInt() and 0xFF)) return null
        return (frame[4].toInt() and 0xFF) to payload
    }

    /** 帧 → 可写入设备的字节流：`#<小写十六进制>\n` 的 ASCII 编码 */
    fun wrapFrame(frame: ByteArray): ByteArray {
        val sb = StringBuilder(frame.size * 2 + 2)
        sb.append(WRAP_PREFIX)
        frame.forEach { b ->
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        sb.append(WRAP_SUFFIX)
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    /** [wrapFrame] 的逆操作：从设备回包行里还原帧字节，格式不符返回 null */
    fun unwrapFrame(raw: ByteArray): ByteArray? {
        if (raw.size < 3) return null
        val text = raw.toString(Charsets.US_ASCII)
        val start = text.indexOf(WRAP_PREFIX)
        if (start < 0) return null
        val end = text.indexOf(WRAP_SUFFIX, start + 1).let { if (it < 0) text.length else it }
        return hexToBytes(text.substring(start + 1, end))
    }

    // ── 设备回包 ──

    /**
     * 一条回包的解析结果。
     *
     * 请求帧里 `[3]` 是 0x80，回包里 `[3]` 变成 0x81，成败只能看**数据体首字节**：
     * 0x80 是成功，否则第 2 字节是错误码（官方 iOS SDK `KRWMUtils.isSuccessResponseWithData:` 口径）。
     */
    data class BtResponse(
        val functionCode: Int,
        val payload: ByteArray,
        val success: Boolean,
        val errorCode: Int?,
    ) {
        val errorText: String get() = errorCode?.let(::errorCodeText) ?: "-"
        val payloadHex: String get() = bytesToHex(payload)
    }

    const val RESPONSE_OK_MARK = 0x80

    /** 官方 `KRErrorCodeUnknownError`，数据体不足两字节时取它 */
    const val ERROR_UNKNOWN = 0xFF

    /** 解析一条回包帧，帧结构对不上返回 null */
    fun decodeResponse(frame: ByteArray): BtResponse? {
        val (code, payload) = parseFrame(frame) ?: return null
        val success = payload.isNotEmpty() && (payload[0].toInt() and 0xFF) == RESPONSE_OK_MARK
        val errorCode = when {
            success -> null
            payload.size >= 2 -> payload[1].toInt() and 0xFF
            else -> ERROR_UNKNOWN
        }
        return BtResponse(code, payload, success, errorCode)
    }

    /** 错误码中文名，取自官方头文件 KRWMDefine.h 的 KRErrorCode */
    fun errorCodeText(code: Int): String = when (code) {
        0x01 -> "包头错误"
        0x02 -> "包长度错误"
        0x04 -> "功能码错误（设备不认这条命令）"
        0x05 -> "数据格式错误（参数不对）"
        0x06 -> "校验和错误"
        0x07 -> "结束码错误"
        0x08 -> "来源错误"
        ERROR_UNKNOWN -> "未知错误"
        else -> "未登记错误码"
    }

    // ── 设备状态（查询响应里的 deviceState）──

    const val STATE_IDLE = 0
    const val STATE_IN_ORDER = 1
    const val STATE_CARD_CONSUMING = 2
    const val STATE_FINISHED_UNCOLLECTED = 3
    const val STATE_REMOTE_CONTROL = 5

    /**
     * 6：官方头文件里没有这个状态。实测出现在停阀之后、状态 3 之前，持续五秒以上，
     * 按「结算中/记录写入中」理解：这时候发采集命令也拿不到记录。
     */
    const val STATE_SETTLING = 6

    fun stateText(state: Int): String = when (state) {
        STATE_IDLE -> "空闲"
        STATE_IN_ORDER -> "有进行中的订单"
        STATE_CARD_CONSUMING -> "刷卡消费中"
        STATE_FINISHED_UNCOLLECTED -> "消费完成，数据待采集"
        STATE_REMOTE_CONTROL -> "远程控制模式"
        STATE_SETTLING -> "结算中（记录写入中）"
        else -> "未知状态（$state）"
    }

    /** 通用前缀长度：标记(1) + 项目号(4) + 设备号(4) + 账号号(4) + 序号/状态(1) */
    private const val PREFIX_END = 20

    /**
     * 设备查询响应（数据体）的解析结果。
     *
     * [randomNumber] 是设备本次会话生成的交易校验随机数（小写十六进制），
     * 下单签名要拿它参与计算，也是「必须蓝牙连上设备才能开阀」的根本原因。
     */
    data class BtDeviceState(
        val projectId: Int,
        val deviceId: Int,
        val accountId: Int,
        val snCode: String,
        val deviceState: Int,
        val randomNumber: String,
        val mainType: Int?,
        val subType: Int?,
        /** 协议版本号，长包里才有；下单时原样回传 */
        val protocolType: String? = null,
        /** 原始数据体十六进制，偏移对不上时对着抓包结果比 */
        val rawHex: String = "",
    )

    /**
     * 解析设备查询响应，数据体结构按长度分三种（官方 analyWaterDatas 的 case 23/28/48+）：
     *
     * ```
     * [0]        0x80 标记
     * [1..4]     projectId    [5..8] deviceId    [9..12] accountId
     * [13..18]   snCode（6 字节）
     * 短包        [19] 状态，[21..22] 2 字节随机数，25 字节起 [23] 主类型、[24] 子类型
     * 长包        [19] 协议版本，[24..27] 4 字节随机数，[28] 状态，[29] 主类型，[30] 子类型
     * ```
     *
     * 结构对不上（长度不足、标记不符）返回 null。长度分支按「够不够读到该字段」判定，
     * 服务端多返回一个字节不该让整包作废。
     */
    fun parseDeviceState(payload: ByteArray): BtDeviceState? {
        if (payload.size < PREFIX_END) return null
        if ((payload[0].toInt() and 0xFF) != RESPONSE_OK_MARK) return null

        val projectId = readInt(payload, 1)
        val deviceId = readInt(payload, 5)
        val accountId = readInt(payload, 9)
        val snCode = bytesToHex(payload.copyOfRange(13, 19))
        val deviceState = payload[19].toInt() and 0xFF

        return when {
            // 长包：本校实测 48 字节，随机数四字节在 [24..27]
            payload.size >= 31 -> BtDeviceState(
                projectId = projectId,
                deviceId = deviceId,
                accountId = accountId,
                snCode = snCode,
                deviceState = payload[28].toInt() and 0xFF,
                randomNumber = bytesToHex(payload.copyOfRange(24, 28)),
                mainType = payload[29].toInt() and 0xFF,
                subType = payload[30].toInt() and 0xFF,
                protocolType = bytesToHex(byteArrayOf(payload[19])),
                rawHex = bytesToHex(payload),
            )

            payload.size >= 25 -> BtDeviceState(
                projectId = projectId,
                deviceId = deviceId,
                accountId = accountId,
                snCode = snCode,
                deviceState = deviceState,
                randomNumber = bytesToHex(payload.copyOfRange(21, 23)),
                mainType = payload[23].toInt() and 0xFF,
                subType = payload[24].toInt() and 0xFF,
                rawHex = bytesToHex(payload),
            )

            else -> BtDeviceState(
                projectId = projectId,
                deviceId = deviceId,
                accountId = accountId,
                snCode = snCode,
                deviceState = deviceState,
                randomNumber = bytesToHex(payload.copyOfRange(21, 23)),
                mainType = null,
                subType = null,
                rawHex = bytesToHex(payload),
            )
        }
    }

    // ── 消费记录（采集响应）──

    /**
     * 0x85 回包的数据体（消费记录）。
     *
     * 字段偏移有两份独立来源互相印证：官方 iOS SDK 的
     * `setConsumptionDetailsFromData:` 反汇编与看雪分析里 analyWaterDatas 的反编译完全一致。
     * [tac] 只在 63 字节长记录里出现；本校设备回 42 字节短记录，拿不到它。
     */
    data class BtConsumptionRecord(
        /** 时间序号 yyMMddHHmmss（BCD），一条记录的唯一标识 */
        val timeIdHex: String,
        val projectId: Int,
        val deviceId: Int,
        val accountId: Int,
        val accountType: Int,
        val useCount: Int,
        /** 预扣金额，单位厘 */
        val preDeductMoney: Int,
        /** 本次消费，单位厘 */
        val consumeMoney: Int,
        val rate: Int,
        val macAddress: String,
        /** 校验码，长记录才有 */
        val tac: String? = null,
        val rawHex: String,
    )

    /** 短记录长度：官方按 length >= 42 走短记录分支 */
    private const val CONSUME_MIN_SIZE = 42

    /** 带 tac 的长记录长度，官方按 == 63 判定 */
    private const val CONSUME_TAC_SIZE = 63

    /**
     * 解析消费记录。数据体偏移：
     *
     * ```
     * [0]      0x80 成功标记（不属于记录本身）
     * [1..6]   时间序号 yyMMddHHmmss
     * [7..10]  项目号   [11..14] 设备号   [15..18] 账号号
     * [19]     账户类别  [20..23] 使用次数
     * [24..27] 预扣金额  [28..31] 本次消费  [32..35] 费率   [36..41] MAC
     * ```
     */
    fun parseConsumption(payload: ByteArray): BtConsumptionRecord? {
        if (payload.size < CONSUME_MIN_SIZE) return null
        if ((payload[0].toInt() and 0xFF) != RESPONSE_OK_MARK) return null
        return BtConsumptionRecord(
            timeIdHex = bytesToHex(payload.copyOfRange(1, 7)),
            projectId = readInt(payload, 7),
            deviceId = readInt(payload, 11),
            accountId = readInt(payload, 15),
            accountType = payload[19].toInt() and 0xFF,
            useCount = readInt(payload, 20),
            preDeductMoney = readInt(payload, 24),
            consumeMoney = readInt(payload, 28),
            rate = readInt(payload, 32),
            macAddress = bytesToHex(payload.copyOfRange(36, 42)),
            tac = if (payload.size >= CONSUME_TAC_SIZE) bytesToHex(payload.copyOfRange(58, 63)) else null,
            rawHex = bytesToHex(payload),
        )
    }

    // ── 清除命令的参数候选 ──

    /**
     * 一条待试的清除命令。[key] 是稳定标识（不含设备信息）：试通哪条之后记在本机，
     * 下次直接排到最前面。不能用 payload 当标识——参数随当次记录变化。
     */
    data class BtClearCandidate(
        val key: String,
        val label: String,
        val functionCode: Int,
        val payload: ByteArray,
    ) {
        val payloadHex: String get() = bytesToHex(payload)
    }

    /** 上次试通的候选标识；试通后由调用方更新 */
    @Volatile
    var preferredClearKey: String? = null

    /**
     * 设备清除命令要的「记录摘要」：时间序号 + 项目号 + 设备号 + 账号号 + 使用次数，共 22 字节。
     *
     * 不是猜的：服务端凭据 clData 解密后的第二段就是这个布局，把它发进设备，
     * 设备立刻回到空闲（本校真机验证）。所以清除不必依赖服务端凭据——
     * 从刚读回的记录里切片就够，记录早已结算过、上传被拒时照样能清。
     */
    fun recordDigest(record: ByteArray): ByteArray? {
        if (record.size < CONSUME_MIN_SIZE) return null
        return record.copyOfRange(1, 19) + record.copyOfRange(20, 24)
    }

    /** [recordDigest] 的另一种读法：末四字节是账户类别（大端补成四字节） */
    fun recordDigestWithAccountType(record: ByteArray): ByteArray? {
        if (record.size < CONSUME_MIN_SIZE) return null
        val accountType = record[19].toInt() and 0xFF
        return record.copyOfRange(1, 19) + byteArrayOf(0, 0, 0, accountType.toByte())
    }

    /**
     * 清除命令（0x86）的参数候选，**实测确认过的记录摘要排第一**；
     * 服务端凭据与记录的其余切法留作保险。[preferredKey] 是上次试通的那条，给了就排最前。
     */
    fun clearCandidates(
        record: ByteArray,
        clDataPlain: ByteArray? = null,
        preferredKey: String? = null,
    ): List<BtClearCandidate> {
        val out = mutableListOf<BtClearCandidate>()

        if (record.size >= CONSUME_MIN_SIZE) {
            recordDigest(record)?.let {
                out += BtClearCandidate(ClearKey.RECORD_DIGEST, "记录摘要（时间+项目+设备+账号+次数）", CODE_CLEAR, it)
            }
            recordDigestWithAccountType(record)?.let {
                out += BtClearCandidate(ClearKey.RECORD_DIGEST_TYPE, "记录摘要（末四字节按账户类别）", CODE_CLEAR, it)
            }
        }

        if (clDataPlain != null && clDataPlain.isNotEmpty()) {
            val text = clDataPlain.toString(Charsets.US_ASCII)
            out += BtClearCandidate(ClearKey.CL_DATA_RAW, "服务端凭据原文", CODE_CLEAR, clDataPlain)
            hexToBytes(text)?.takeIf { it.isNotEmpty() }?.let {
                out += BtClearCandidate(ClearKey.CL_DATA_HEX, "服务端凭据按十六进制解", CODE_CLEAR, it)
            }
            val parts = text.split('-').filter { it.isNotEmpty() }
            parts.forEachIndexed { index, part ->
                hexToBytes(part)?.takeIf { it.isNotEmpty() }?.let {
                    out += BtClearCandidate(ClearKey.CL_DATA_PART + index, "服务端凭据第 ${index + 1} 段", CODE_CLEAR, it)
                }
            }
            if (parts.size > 1) {
                hexToBytes(parts.joinToString(""))?.takeIf { it.isNotEmpty() }?.let {
                    out += BtClearCandidate(ClearKey.CL_DATA_JOINED, "服务端凭据两段拼起来", CODE_CLEAR, it)
                }
            }
        }

        if (record.size >= CONSUME_MIN_SIZE) {
            out += BtClearCandidate(ClearKey.RECORD_FULL, "整条消费记录", CODE_CLEAR, record)
            out += BtClearCandidate(ClearKey.RECORD_NO_MARK, "记录去掉标记字节", CODE_CLEAR, record.copyOfRange(1, record.size))
            val timeId = record.copyOfRange(1, 7)
            val mac = record.copyOfRange(36, 42)
            out += BtClearCandidate(ClearKey.RECORD_TIME_ID, "时间序号（6 字节）", CODE_CLEAR, timeId)
            out += BtClearCandidate(ClearKey.RECORD_TIME_ID_MAC, "时间序号 + 设备 MAC", CODE_CLEAR, timeId + mac)
            out += BtClearCandidate(ClearKey.RECORD_MAC, "设备 MAC（6 字节）", CODE_CLEAR, mac)
        }

        out += BtClearCandidate(ClearKey.EMPTY, "空参数", CODE_CLEAR, byteArrayOf(0x00))

        val distinct = out.distinctBy { QzxyBtProtocol.bytesToHex(it.payload) }
        val preferred = preferredKey?.let { key -> distinct.firstOrNull { it.key == key } }
        return if (preferred == null) distinct else listOf(preferred) + distinct.filter { it !== preferred }
    }

    /** 清除候选的稳定标识 */
    object ClearKey {
        const val RECORD_DIGEST = "record.digest"
        const val RECORD_DIGEST_TYPE = "record.digestType"
        const val CL_DATA_RAW = "clData.raw"
        const val CL_DATA_HEX = "clData.hex"
        const val CL_DATA_PART = "clData.part"
        const val CL_DATA_JOINED = "clData.joined"
        const val RECORD_FULL = "record.full"
        const val RECORD_NO_MARK = "record.noMarker"
        const val RECORD_TIME_ID = "record.timeId"
        const val RECORD_TIME_ID_MAC = "record.timeIdMac"
        const val RECORD_MAC = "record.mac"
        const val EMPTY = "empty"
    }

    // ── 蓝牙接口签名 ──

    /**
     * 第二次哈希前固定追加的后缀，自带 `&key=` 前缀。来自官方 libklcxkjencry.so
     * 反汇编（.rodata 0xa1fd0 的全局串）；下单与上传接口都用它。
     */
    const val SIGN_SUFFIX = "&key=sign-kailu-855c74a88b8b494187c99b08b8c9a744"

    /**
     * 生成 signature：字段按键名字典序拼成「键名+值」，两次小写 MD5，
     * 第一次 `&key=` 后挂 loginCode，第二次挂固定盐。
     */
    fun signature(fields: Map<String, String>, loginCode: String): String {
        val plain = fields.toSortedMap().entries.joinToString("") { it.key + it.value }
        return md5Lower(md5Lower(plain + "&key=" + loginCode) + SIGN_SUFFIX)
    }

    /** 单层 MD5，小写 32 位 */
    fun md5Lower(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }

    // ── clData 解密（服务端签发的清除凭据）──

    /** AES-128 密钥，官方 so 里的常量；ECB 模式下 IV 用不到，CBC 回退时才用 */
    private const val CL_DATA_KEY = "20210118klcx@002"
    private const val CL_DATA_IV = "1234567876543210"
    private const val CL_BLOCK_SIZE = 16

    /**
     * 解密上传结算响应里的 clData。密文按十六进制或 Base64 解，哪种能解出 16 的整数倍用哪种。
     * 模式按官方分析是 ECB 无填充（解出来再手工剥 PKCS#7），解不出可打印文本时回退 CBC 一次。
     * 长度不对、解密失败返回 null。
     */
    fun decryptClData(encoded: String?): ByteArray? {
        val text = encoded?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val cipherText = decodeClCipherText(text) ?: return null
        val key = SecretKeySpec(CL_DATA_KEY.toByteArray(Charsets.US_ASCII), "AES")
        val ecb = runCatching {
            stripPadding(transform("AES/ECB/NoPadding", key, cipherText, null))
        }.getOrNull()
        if (ecb != null && isPrintable(ecb)) return ecb
        val cbc = runCatching {
            stripPadding(
                transform("AES/CBC/NoPadding", key, cipherText, IvParameterSpec(CL_DATA_IV.toByteArray(Charsets.US_ASCII))),
            )
        }.getOrNull()
        if (cbc != null && isPrintable(cbc)) return cbc
        return ecb
    }

    private fun transform(algorithm: String, key: SecretKeySpec, cipherText: ByteArray, iv: IvParameterSpec?): ByteArray {
        val cipher = Cipher.getInstance(algorithm)
        if (iv == null) cipher.init(Cipher.DECRYPT_MODE, key) else cipher.init(Cipher.DECRYPT_MODE, key, iv)
        return cipher.doFinal(cipherText)
    }

    private fun decodeClCipherText(text: String): ByteArray? {
        hexToBytes(text)?.takeIf { it.size % CL_BLOCK_SIZE == 0 }?.let { return it }
        return runCatching { Base64.getDecoder().decode(text) }
            .getOrNull()
            ?.takeIf { it.size % CL_BLOCK_SIZE == 0 }
    }

    private fun isPrintable(bytes: ByteArray): Boolean =
        bytes.isNotEmpty() && bytes.all { (it.toInt() and 0xFF) in 0x20..0x7E }

    /** 剥 PKCS#7 填充；填充不合法就原样返回 */
    private fun stripPadding(plain: ByteArray): ByteArray {
        if (plain.isEmpty()) return plain
        val pad = plain.last().toInt() and 0xFF
        if (pad !in 1..CL_BLOCK_SIZE || pad > plain.size) return plain
        if (plain.takeLast(pad).any { (it.toInt() and 0xFF) != pad }) return plain
        return plain.copyOfRange(0, plain.size - pad)
    }

    // ── 十六进制工具 ──

    private val HEX = "0123456789abcdef".toCharArray()

    /** 字节 → 小写十六进制，与设备侧编码口径一致 */
    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        bytes.forEach { b ->
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    /** 十六进制文本 → 字节；含非十六进制字符或长度为奇数时返回 null */
    fun hexToBytes(hex: String): ByteArray? {
        val trimmed = hex.trim()
        if (trimmed.isEmpty() || trimmed.length % 2 != 0) return null
        val out = ByteArray(trimmed.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(trimmed[i * 2], 16)
            val lo = Character.digit(trimmed[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        var value = 0
        for (i in 0 until 4) {
            value = (value shl 8) or (bytes[offset + i].toInt() and 0xFF)
        }
        return value
    }
}
