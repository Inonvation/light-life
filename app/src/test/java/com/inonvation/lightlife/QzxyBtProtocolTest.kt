package com.inonvation.lightlife

import com.inonvation.lightlife.data.qzxy.QzxyBtProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 趣智蓝牙款帧协议。样本与固定向量均来自已真机闭环验证的实现（JUWP-schedule，
 * 本校设备开阀出水、结算 ¥0.04、清除回空闲），改协议这一组会先红。
 */
class QzxyBtProtocolTest {

    // ── 帧编解码 ──

    @Test
    fun `查询设备帧与实测样本逐字节一致`() {
        val frame = QzxyBtProtocol.encodeFrame(QzxyBtProtocol.CODE_QUERY_DEVICE)
        assertEquals("60000480230000a316", QzxyBtProtocol.bytesToHex(frame))
    }

    @Test
    fun `多字节数据体长度与校验和同步变化`() {
        val payload = byteArrayOf(0x11, 0x22, 0x33)
        val frame = QzxyBtProtocol.encodeFrame(QzxyBtProtocol.CODE_DOWN_RATE, payload)
        // 长度字段 = 数据体 + 3；校验和 = 0x80 + 功能码 + 数据体字节和
        assertEquals(0x00, frame[1].toInt())
        assertEquals(0x06, frame[2].toInt())
        val expectedSum = (0x80 + QzxyBtProtocol.CODE_DOWN_RATE + 0x11 + 0x22 + 0x33) and 0xFF
        assertEquals(expectedSum.toByte(), frame[frame.size - 2])
        val (code, body) = QzxyBtProtocol.parseFrame(frame)!!
        assertEquals(QzxyBtProtocol.CODE_DOWN_RATE, code)
        assertArrayEquals(payload, body)
    }

    @Test
    fun `文本包装与还原互逆`() {
        val frame = QzxyBtProtocol.encodeFrame(QzxyBtProtocol.CODE_QUERY_DEVICE)
        val wrapped = QzxyBtProtocol.wrapFrame(frame)
        assertEquals("#60000480230000a316\n", wrapped.toString(Charsets.US_ASCII))
        assertArrayEquals(frame, QzxyBtProtocol.unwrapFrame(wrapped))
    }

    @Test
    fun `校验和被改动时解析失败`() {
        val frame = QzxyBtProtocol.hexToBytes("60000480230000a316")!!
        frame[frame.size - 2] = 0x00
        assertNull(QzxyBtProtocol.parseFrame(frame))
    }

    @Test
    fun `长度不足与包头不对的帧都拒绝`() {
        assertNull(QzxyBtProtocol.parseFrame(ByteArray(7)))
        assertNull(QzxyBtProtocol.parseFrame(QzxyBtProtocol.hexToBytes("61000480230000a316")!!))
    }

    // ── 回包成败判定 ──

    @Test
    fun `回包控制字节是 0x81 也能解出功能码与成败`() {
        val body = byteArrayOf(0x80.toByte(), 0x01, 0x02)
        val frame = QzxyBtProtocol.encodeFrame(QzxyBtProtocol.CODE_COLLECT, body)
        frame[3] = 0x81.toByte() // 设备回包的 [3] 不是 0x80 而是 0x81
        // 重算校验和
        var sum = 0x81 + QzxyBtProtocol.CODE_COLLECT
        body.forEach { sum += it.toInt() and 0xFF }
        frame[frame.size - 2] = (sum and 0xFF).toByte()

        val response = QzxyBtProtocol.decodeResponse(frame)!!
        assertEquals(QzxyBtProtocol.CODE_COLLECT, response.functionCode)
        assertTrue(response.success)
        assertNull(response.errorCode)
    }

    @Test
    fun `数据体首字节非 0x80 时取第二字节为错误码`() {
        val body = byteArrayOf(0x81.toByte(), 0x04)
        val frame = QzxyBtProtocol.encodeFrame(QzxyBtProtocol.CODE_END_RATE, body)
        frame[3] = 0x81.toByte()
        var sum = 0x81 + QzxyBtProtocol.CODE_END_RATE
        body.forEach { sum += it.toInt() and 0xFF }
        frame[frame.size - 2] = (sum and 0xFF).toByte()

        val response = QzxyBtProtocol.decodeResponse(frame)!!
        assertFalse(response.success)
        assertEquals(0x04, response.errorCode)
        assertEquals("功能码错误（设备不认这条命令）", response.errorText)
    }

    @Test
    fun `数据体只有一个字节时错误码按未知处理`() {
        val body = byteArrayOf(0x81.toByte())
        val frame = QzxyBtProtocol.encodeFrame(QzxyBtProtocol.CODE_END_RATE, body)
        frame[3] = 0x81.toByte()
        var sum = 0x81 + QzxyBtProtocol.CODE_END_RATE + 0x81
        frame[frame.size - 2] = (sum and 0xFF).toByte()

        val response = QzxyBtProtocol.decodeResponse(frame)!!
        assertFalse(response.success)
        assertEquals(QzxyBtProtocol.ERROR_UNKNOWN, response.errorCode)
    }

    // ── 设备查询响应 ──

    private fun deviceStatePayload(size: Int): ByteArray {
        val bytes = ByteArray(size)
        bytes[0] = 0x80.toByte()
        writeInt(bytes, 1, 42)
        writeInt(bytes, 5, 268)
        writeInt(bytes, 9, 2605)
        byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06).copyInto(bytes, 13)
        return bytes
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) {
            target[offset + i] = ((value shr (8 * (3 - i))) and 0xFF).toByte()
        }
    }

    @Test
    fun `短包解析出项目设备账号序列号与状态`() {
        val bytes = deviceStatePayload(23)
        bytes[19] = 0x01
        bytes[21] = 0xAB.toByte()
        bytes[22] = 0xCD.toByte()

        val state = QzxyBtProtocol.parseDeviceState(bytes)!!
        assertEquals(42, state.projectId)
        assertEquals(268, state.deviceId)
        assertEquals(2605, state.accountId)
        assertEquals("010203040506", state.snCode)
        assertEquals(QzxyBtProtocol.STATE_IN_ORDER, state.deviceState)
        assertEquals("abcd", state.randomNumber)
        assertNull(state.mainType)
    }

    @Test
    fun `长包解析出四字节随机数与协议版本`() {
        // 本校实测 48 字节长包：随机数 [24..27]，状态 [28]，主类型 [29]，子类型 [30]，协议版本 [19]
        val bytes = deviceStatePayload(48)
        byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()).copyInto(bytes, 24)
        bytes[19] = 0x01
        bytes[28] = 0x00
        bytes[29] = 0x00
        bytes[30] = 0x03

        val state = QzxyBtProtocol.parseDeviceState(bytes)!!
        assertEquals("deadbeef", state.randomNumber)
        assertEquals(QzxyBtProtocol.STATE_IDLE, state.deviceState)
        assertEquals(0, state.mainType)
        assertEquals(3, state.subType)
        assertEquals("01", state.protocolType)
    }

    @Test
    fun `标记不符或长度不足的查询响应返回 null`() {
        val bytes = deviceStatePayload(48)
        bytes[0] = 0x00
        assertNull(QzxyBtProtocol.parseDeviceState(bytes))
        assertNull(QzxyBtProtocol.parseDeviceState(ByteArray(19)))
    }

    // ── 消费记录 ──

    @Test
    fun `消费记录按官方偏移解析`() {
        val record = ByteArray(42)
        record[0] = 0x80.toByte()
        // 时间序号 yyMMddHHmmss 的 BCD 串
        byteArrayOf(0x26, 0x09, 0x27, 0x22, 0x00, 0x37).copyInto(record, 1)
        writeInt(record, 7, 42)
        writeInt(record, 11, 268)
        writeInt(record, 15, 2605)
        record[19] = 0x02
        writeInt(record, 20, 7)
        writeInt(record, 24, 1045)
        writeInt(record, 28, 40)
        writeInt(record, 32, 139)
        byteArrayOf(0x00, 0x10, 0x45, 0x00, 0x00, 0xA0.toByte()).copyInto(record, 36)

        val parsed = QzxyBtProtocol.parseConsumption(record)!!
        assertEquals("260927220037", parsed.timeIdHex)
        assertEquals(42, parsed.projectId)
        assertEquals(268, parsed.deviceId)
        assertEquals(2605, parsed.accountId)
        assertEquals(2, parsed.accountType)
        assertEquals(7, parsed.useCount)
        assertEquals(1045, parsed.preDeductMoney)
        assertEquals(40, parsed.consumeMoney)
        assertEquals(139, parsed.rate)
        assertEquals("0010450000a0", parsed.macAddress)
        assertNull(parsed.tac)
    }

    @Test
    fun `长记录带 tac 短记录没有`() {
        val long = ByteArray(63)
        long[0] = 0x80.toByte()
        byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte())
            .copyInto(long, 58)
        assertEquals("aabbccddee", QzxyBtProtocol.parseConsumption(long)!!.tac)
        val short = ByteArray(42)
        short[0] = 0x80.toByte()
        assertNull(QzxyBtProtocol.parseConsumption(short)!!.tac)
    }

    @Test
    fun `长度不足四十二字节的数据体不是消费记录`() {
        assertNull(QzxyBtProtocol.parseConsumption(ByteArray(41)))
    }

    // ── 记录摘要（清除参数）──

    @Test
    fun `记录摘要等于 clData 凭据的第二段`() {
        // 真机样本：clData 明文 = 时间戳-清除参数，右侧 22 字节与记录切片同源
        val record = ByteArray(42)
        record[0] = 0x80.toByte()
        QzxyBtProtocol.hexToBytes("2609272200370000011f000010450000a03a")!!
            .copyInto(record, 1)
        writeInt(record, 20, 2)

        val digest = QzxyBtProtocol.recordDigest(record)!!
        assertEquals("2609272200370000011f000010450000a03a00000002", QzxyBtProtocol.bytesToHex(digest))
        assertEquals(22, digest.size)
    }

    @Test
    fun `记录摘要的另一种读法末四字节是账户类别`() {
        val record = ByteArray(42)
        record[0] = 0x80.toByte()
        record[19] = 0x02
        val digest = QzxyBtProtocol.recordDigestWithAccountType(record)!!
        assertEquals("00000002", QzxyBtProtocol.bytesToHex(digest.copyOfRange(18, 22)))
    }

    @Test
    fun `清除候选表把记录摘要排最前且可按偏好重排`() {
        val record = ByteArray(42)
        record[0] = 0x80.toByte()
        val candidates = QzxyBtProtocol.clearCandidates(record)
        assertEquals(QzxyBtProtocol.ClearKey.RECORD_DIGEST, candidates.first().key)
        assertTrue(candidates.size > 5)

        val preferred = QzxyBtProtocol.clearCandidates(record, preferredKey = QzxyBtProtocol.ClearKey.EMPTY)
        assertEquals(QzxyBtProtocol.ClearKey.EMPTY, preferred.first().key)
        // 去重后偏好候选只出现一次
        assertEquals(1, preferred.count { it.key == QzxyBtProtocol.ClearKey.EMPTY })
    }

    // ── 签名 ──

    @Test
    fun `签名与官方算法固定向量一致`() {
        // 向量取自 JUWP-schedule QzxySignTest：md5(md5(键值拼接 + &key=登录码) + 固定盐)
        val sign = QzxyBtProtocol.signature(
            fields = mapOf(
                "telephone" to "18613960123",
                "deviceId" to "268",
                "xfModel" to "0",
                "randomNumber" to "abcd",
            ),
            loginCode = "loginCodeXYZ",
        )
        assertEquals("ace6933afd653fdb3b1498daa5c7ba35", sign)
        assertEquals(sign.lowercase(), sign)
    }

    @Test
    fun `字段顺序不影响签名`() {
        val fields = mapOf("telephone" to "123", "deviceId" to "268")
        val reversed = fields.entries.reversed().associate { it.key to it.value }
        assertEquals(
            QzxyBtProtocol.signature(fields, "code"),
            QzxyBtProtocol.signature(reversed, "code"),
        )
    }

    @Test
    fun `固定盐自带 key 前缀`() {
        assertTrue(QzxyBtProtocol.SIGN_SUFFIX.startsWith("&key="))
    }

    // ── clData 解密 ──

    @Test
    fun `clData 用官方密钥可解回原文`() {
        val plain = "1790517647206-2609272200370000011f000010450000a03a00000002"
        val cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec("20210118klcx@002".toByteArray(Charsets.US_ASCII), "AES"),
        )
        val encrypted = QzxyBtProtocol.bytesToHex(cipher.doFinal(plain.toByteArray(Charsets.US_ASCII)))
        assertEquals(plain, QzxyBtProtocol.decryptClData(encrypted)?.toString(Charsets.US_ASCII))
    }

    @Test
    fun `Base64 密文也能解`() {
        val plain = "260927203533-abcdef"
        val cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec("20210118klcx@002".toByteArray(Charsets.US_ASCII), "AES"),
        )
        val encoded = java.util.Base64.getEncoder().encodeToString(cipher.doFinal(plain.toByteArray(Charsets.US_ASCII)))
        assertEquals(plain, QzxyBtProtocol.decryptClData(encoded)?.toString(Charsets.US_ASCII))
    }

    @Test
    fun `空值与长度不对的密文返回 null`() {
        assertNull(QzxyBtProtocol.decryptClData(null))
        assertNull(QzxyBtProtocol.decryptClData(""))
        assertNull(QzxyBtProtocol.decryptClData("aabb"))
    }

    // ── 十六进制工具 ──

    @Test
    fun `十六进制与字节互逆且小写`() {
        assertEquals("0a1f", QzxyBtProtocol.bytesToHex(byteArrayOf(0x0A, 0x1F)))
        assertArrayEquals(byteArrayOf(0x0A, 0x1F), QzxyBtProtocol.hexToBytes("0A1f"))
        assertNull(QzxyBtProtocol.hexToBytes("abc"))
        assertNull(QzxyBtProtocol.hexToBytes("zz"))
        assertNull(QzxyBtProtocol.hexToBytes(""))
    }

    @Test
    fun `开启阀指令的数据体就是服务端 downData`() {
        val downData = "1122334455"
        val frame = QzxyBtProtocol.encodeFrame(
            QzxyBtProtocol.CODE_DOWN_RATE,
            QzxyBtProtocol.hexToBytes(downData)!!,
        )
        val (code, body) = QzxyBtProtocol.parseFrame(frame)!!
        assertEquals(QzxyBtProtocol.CODE_DOWN_RATE, code)
        assertEquals(downData, QzxyBtProtocol.bytesToHex(body))
        assertNotNull(body)
    }
}
