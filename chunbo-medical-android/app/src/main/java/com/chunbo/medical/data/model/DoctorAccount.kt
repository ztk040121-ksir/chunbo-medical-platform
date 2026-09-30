package com.chunbo.medical.data.model

import com.google.gson.annotations.SerializedName

data class DoctorAccount(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("doctorName") val doctorName: String? = null,
    @SerializedName("doctorId") val doctorId: String? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("specialty") val specialty: String? = null,
    @SerializedName("consultationFee") val consultationFee: Double? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("introduction") val introduction: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("status") val status: String? = null
)

data class DoctorListResponse(
    @SerializedName("success") val success: Boolean? = true,
    @SerializedName("total") val total: Int? = 0,
    @SerializedName("data") val data: List<DoctorAccount>? = emptyList()
)
