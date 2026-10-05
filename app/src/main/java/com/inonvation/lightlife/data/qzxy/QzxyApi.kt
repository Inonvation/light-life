package com.inonvation.lightlife.data.qzxy

import com.inonvation.lightlife.data.EmptyData
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * 趣智校园 Retrofit 接口（一期：登录、钱包、设备、洗澡订单）。
 * 认证方式：loginCode 等参数随 GET 的 Query 或 POST 的 Form 传递，见 QzxySession。
 */
interface QzxyApi {
    /** 密码登录。密码需经 QzxyPassword 加密（MD5 取后 10 位大写） */
    @FormUrlEncoded
    @POST("user/login")
    suspend fun login(
        @Field("telephone") telephone: String,
        @Field("password") password: String,
        @Field("phoneSystem") phoneSystem: String = QzxyApiConfig.PHONE_SYSTEM,
        @Field("type") type: Int = 0,
        @Field("version") version: String = QzxyApiConfig.VERSION,
    ): QzxyEnvelope<QzxyLoginData>

    @GET("account/wallet")
    suspend fun getWallet(@QueryMap auth: Map<String, String>): QzxyEnvelope<QzxyWalletData>

    /** 通过 MAC 地址查设备详情，拿到控制设备必需的 snCode */
    @GET("device/info/mac")
    suspend fun getDeviceInfo(
        @Query("macAddress") mac: String,
        @QueryMap auth: Map<String, String>,
    ): QzxyEnvelope<QzxyDeviceInfo>

    /** 开始洗澡（开阀）。data 中可带 orderNo/autoDisConTime/preDeductMoney；orderNo 缺失时需轮询 queryUsing */
    @FormUrlEncoded
    @POST("order/tcpDevice/downRate/rateOrder")
    suspend fun downRate(
        @Field("xfModel") xfModel: Int = 0,
        @Field("snCode") snCode: String,
        @FieldMap auth: Map<String, String>,
    ): QzxyEnvelope<QzxyDownRateResult>

    /** 开阀结果确认，可拿到订单号、预扣金额和闲置自动关停秒数 */
    @FormUrlEncoded
    @POST("order/tcpDevice/query/downRateResult")
    suspend fun downRateResult(
        @Field("snCode") snCode: String,
        @FieldMap auth: Map<String, String>,
    ): QzxyEnvelope<QzxyDownRateResult>

    /** 停止洗澡（关阀） */
    @FormUrlEncoded
    @POST("order/tcpDevice/closeOrder")
    suspend fun closeOrder(
        @Field("snCode") snCode: String,
        @Field("orderNo") orderNo: String,
        @FieldMap auth: Map<String, String>,
    ): QzxyEnvelope<EmptyData>

    /** 关阀结果确认，通常携带最终消费金额 */
    @FormUrlEncoded
    @POST("order/tcpDevice/closeOrder/result/query")
    suspend fun closeOrderResult(
        @Field("snCode") snCode: String,
        @Field("orderNo") orderNo: String,
        @FieldMap auth: Map<String, String>,
    ): QzxyEnvelope<QzxyCloseOrderResult>

    /** 消费结算查询（关阀确认未带金额时的兜底） */
    @FormUrlEncoded
    @POST("order/consumeOrder/result/query")
    suspend fun consumeOrderResult(
        @Field("snCode") snCode: String,
        @Field("orderNo") orderNo: String,
        @FieldMap auth: Map<String, String>,
    ): QzxyEnvelope<QzxyConsumeResult>

    /** 查询设备进行中的订单。errorCode 307 = 设备正被使用（此时 data 可能仍带订单信息） */
    @FormUrlEncoded
    @POST("order/tcpDevice/query/rateOrder/using")
    suspend fun queryUsing(
        @Field("xfModel") xfModel: Int = 0,
        @Field("snCode") snCode: String,
        @FieldMap auth: Map<String, String>,
    ): QzxyEnvelope<QzxyOrderStatus>

    // ── 蓝牙直控（communicationTypeId=0 的设备）──

    /** 蓝牙款下单：响应 data.downData 为需经手机蓝牙发给设备的开阀指令 */
    @FormUrlEncoded
    @POST("order/downRate/bluetooth/rateOrder")
    suspend fun btRateOrder(@FieldMap params: Map<String, String>): QzxyEnvelope<QzxyBtRateOrderData>

    /** 蓝牙款结算：上传设备采集的消费数据 xfData，响应携带 orderNo 与本次消费金额 */
    @FormUrlEncoded
    @POST("order/upload/bluetooth/data")
    suspend fun btUploadData(@FieldMap params: Map<String, String>): QzxyEnvelope<QzxyBtUploadData>

    /** 键盘使用码：在热水器键盘上输入即可开水（无网设备的官方开水方式） */
    @GET("account/useCode/new")
    suspend fun getUseCode(@QueryMap auth: Map<String, String>): QzxyEnvelope<QzxyUseCodeData>
}
