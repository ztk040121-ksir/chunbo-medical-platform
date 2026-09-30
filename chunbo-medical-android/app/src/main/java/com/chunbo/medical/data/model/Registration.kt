package com.chunbo.medical.data.model

import com.google.gson.annotations.SerializedName

data class Registration(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("regNo") val regNo: String? = null,
    @SerializedName("patientId") val patientId: Long? = null,
    @SerializedName("patientName") val patientName: String? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("idCard") val idCard: String? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("doctorName") val doctorName: String? = null,
    @SerializedName("chiefComplaint") val chiefComplaint: String? = null,
    @SerializedName("triageLevel") val triageLevel: String? = null,
    @SerializedName("queueNo") val queueNo: Int? = null,
    @SerializedName("queueNumber") val queueNumber: String? = null,
    @SerializedName("status") var status: String? = null,
    @SerializedName("regType") val regType: String? = null,
    @SerializedName("fee") val fee: Double? = null,
    @SerializedName("symptoms") val symptoms: String? = null,
    @SerializedName("preConsultationData") val preConsultationData: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("createTime") val createTime: String? = null
)
