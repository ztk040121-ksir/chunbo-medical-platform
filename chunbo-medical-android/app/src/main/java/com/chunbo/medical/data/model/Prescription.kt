package com.chunbo.medical.data.model

import com.google.gson.annotations.SerializedName

data class PrescriptionWrapper(
    @SerializedName("prescription") val prescription: PrescriptionDetail? = null,
    @SerializedName("items") val items: List<PrescriptionItem>? = null
)

data class PrescriptionDetail(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("prescriptionNo") val prescriptionNo: String? = null,
    @SerializedName("patientId") val patientId: Long? = null,
    @SerializedName("patientName") val patientName: String? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("doctorName") val doctorName: String? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("diagnosis") val diagnosis: String? = null,
    @SerializedName("symptoms") val symptoms: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("totalPrice") val totalPrice: Double? = null,
    @SerializedName("totalAmount") val totalAmount: Double? = null,
    @SerializedName("aiAdvice") val aiAdvice: String? = null,
    @SerializedName("createTime") val createTime: String? = null
) {
    val displayPrice: Double
        get() = totalAmount ?: totalPrice ?: 0.0
}

data class PrescriptionItem(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("prescriptionId") val prescriptionId: Long? = null,
    @SerializedName("medicineId") val medicineId: Long? = null,
    @SerializedName("medicineName") val medicineName: String? = null,
    @SerializedName("specification") val specification: String? = null,
    @SerializedName("usageMethod") val usageMethod: String? = null,
    @SerializedName("route") val route: String? = null,
    @SerializedName("frequency") val frequency: String? = null,
    @SerializedName("dose") val dose: String? = null,
    @SerializedName("dosage") val dosage: String? = null,
    @SerializedName("quantity") val quantity: Int? = null,
    @SerializedName("unitPrice") val unitPrice: Double? = null,
    @SerializedName("price") val price: Double? = null,
    @SerializedName("totalPrice") val totalPrice: Double? = null,
    @SerializedName("subtotal") val subtotal: Double? = null
) {
    val displayUsage: String
        get() = buildString {
            val d = dosage ?: dose
            val f = frequency
            val r = route ?: usageMethod
            if (!r.isNullOrBlank()) append(r)
            if (!f.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append(f)
            }
            if (!d.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append(d)
            }
        }
}
