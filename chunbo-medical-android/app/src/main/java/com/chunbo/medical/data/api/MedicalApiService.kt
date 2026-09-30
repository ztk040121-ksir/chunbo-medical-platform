package com.chunbo.medical.data.api

import com.chunbo.medical.data.model.DoctorAccount
import com.chunbo.medical.data.model.MallOrder
import com.chunbo.medical.data.model.MallProduct
import com.chunbo.medical.data.model.PrescriptionWrapper
import com.chunbo.medical.data.model.Registration
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MedicalApiService {

    // === 春播商城用户认证与个人中心 ===
    @POST("api/mall/user/login")
    suspend fun login(@Body body: Map<String, String>): Map<String, Any>

    @POST("api/mall/user/register")
    suspend fun register(@Body body: Map<String, String>): Map<String, Any>

    @GET("api/mall/user/info")
    suspend fun getUserInfo(@Header("Authorization") auth: String? = null): Map<String, Any>

    @POST("api/mall/user/profile")
    suspend fun updateProfile(@Body body: Map<String, String>): Map<String, Any>

    @POST("api/mall/user/update-address")
    suspend fun updateAddress(@Body body: Map<String, String>): Map<String, Any>

    @POST("api/mall/user/avatar")
    suspend fun updateAvatar(@Body body: Map<String, String>): Map<String, Any>

    // === 春播健康商城 ===
    @GET("api/mall/products")
    suspend fun getMallProducts(): List<MallProduct>

    @GET("api/mall/orders")
    suspend fun getMallOrders(
        @Query("username") username: String? = null,
        @Query("phone") phone: String? = null
    ): List<MallOrder>

    @retrofit2.http.Multipart
    @POST("api/mall/user/upload-avatar")
    suspend fun uploadAvatarFile(
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part,
        @Query("username") username: String? = null
    ): Map<String, Any>

    @POST("api/mall/order/create")
    suspend fun createMallOrder(@Body body: Map<String, @JvmSuppressWildcards Any>): MallOrder

    // 患者端确认送达（居民签收），状态 已发货 → 已送达
    @POST("api/mall/order/deliver")
    suspend fun confirmOrderDelivered(@Body body: Map<String, String>): Map<String, Any>

    // === AI 执业药师智能体咨询与对话 ===
    @POST("api/mall/chat")
    suspend fun mallChat(@Body body: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    @POST("api/assistant/chat")
    suspend fun chatAssistant(@Body body: Map<String, String>): ResponseBody

    // === 社区医生执业库 ===
    @GET("api/doctor/list")
    suspend fun getDoctorList(@Query("department") department: String? = null): com.chunbo.medical.data.model.DoctorListResponse

    // === 便民就医与预约挂号 ===
    @GET("api/registration/queue")
    suspend fun getQueue(): List<Registration>

    @GET("api/registration/list")
    suspend fun getRegistrationList(
        @Query("status") status: String? = null,
        @Query("keyword") keyword: String? = null,
        @Query("userPhone") userPhone: String? = null
    ): List<Registration>

    @POST("api/registration/create")
    suspend fun createRegistration(@Body body: Map<String, @JvmSuppressWildcards Any>): Registration

    @POST("api/registration/sign/{id}")
    suspend fun signRegistration(@Path("id") id: Long): Registration

    @POST("api/registration/call/{id}")
    suspend fun callPatient(@Path("id") id: Long): Registration

    @POST("api/registration/finish/{id}")
    suspend fun finishPatient(@Path("id") id: Long): Registration

    @POST("api/registration/cancel/{id}")
    suspend fun cancelPatient(@Path("id") id: Long): Registration

    // 手机端修改挂号单（仅待签到/待诊允许：主诉、联系电话、预问诊数据）
    @POST("api/registration/update")
    suspend fun updateRegistration(@Body body: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    // === 处方与病历档案 ===
    @GET("api/prescription/list")
    suspend fun getPrescriptionList(
        @Query("phone") phone: String? = null,
        @Query("patientName") patientName: String? = null
    ): List<PrescriptionWrapper>

    @POST("api/medical/chat/pre-consult/welcome")
    suspend fun preConsultWelcome(@Body body: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    @POST("api/medical/chat/pre-consult/quick-replies")
    suspend fun preConsultQuickReplies(@Body body: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    @POST("api/medical/chat/pre-consult/dialogue")
    suspend fun preConsultDialogue(@Body body: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    @POST("api/medical/chat/pre-consult/extract")
    suspend fun preConsultExtract(@Body body: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    // === 预问诊会话历史记录 ===
    @GET("api/medical/chat/pre-consult/sessions")
    suspend fun getPreConsultSessions(
        @Query("userId") userId: String,
        @Query("patientName") patientName: String? = null,
        @Query("idCard") idCard: String? = null
    ): Map<String, List<Map<String, Any>>>

    @GET("api/medical/chat/pre-consult/session-messages")
    suspend fun getPreConsultSessionMessages(@Query("sessionId") sessionId: String): List<Map<String, String>>

    @DELETE("api/medical/chat/pre-consult/session")
    suspend fun deletePreConsultSession(
        @Query("sessionId") sessionId: String,
        @Query("userId") userId: String
    ): Response<Map<String, Any>>

    // === 统一会话历史管理（与 PC 端完全一致，后端 titleChatClient 提炼标题，按时间段分组） ===
    @GET("api/session/history")
    suspend fun getSessionHistory(
        @Query("bizType") bizType: String,
        @Query("userId") userId: String
    ): Map<String, List<Map<String, Any>>>

    @GET("api/session/messages")
    suspend fun getSessionMessages(
        @Query("sessionId") sessionId: String
    ): List<Map<String, String>>

    @DELETE("api/session/history")
    suspend fun deleteSession(
        @Query("bizType") bizType: String,
        @Query("sessionId") sessionId: String,
        @Query("userId") userId: String
    ): Response<Unit>

    @POST("api/medical/chat/stop")
    suspend fun stopMedicalChat(
        @Query("sessionId") sessionId: String
    ): Response<Unit>

    // === 多模态：附件上传 + 语音转文字 ===
    @retrofit2.http.Multipart
    @POST("api/upload/file")
    suspend fun uploadFile(
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part
    ): Map<String, Any>

    @retrofit2.http.Multipart
    @POST("api/audio/asr")
    suspend fun asr(
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part
    ): Map<String, Any>
}
