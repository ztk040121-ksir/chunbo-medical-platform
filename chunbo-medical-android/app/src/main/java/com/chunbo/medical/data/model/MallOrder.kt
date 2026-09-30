package com.chunbo.medical.data.model

import com.google.gson.annotations.SerializedName

data class MallOrder(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("orderNo") val orderNo: String? = null,
    @SerializedName("productName") val productName: String? = null,
    @SerializedName("productId") val productId: Long? = null,
    @SerializedName("quantity") val quantity: Int? = null,
    @SerializedName("unitPrice") val unitPrice: Double? = null,
    @SerializedName("totalAmount") val totalAmount: Double? = null,
    @SerializedName("finalAmount") val finalAmount: Double? = null,
    @SerializedName("discountAmount") val discountAmount: Double? = null,
    @SerializedName("itemsJson") val itemsJson: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("buyerName") val buyerName: String? = null,
    @SerializedName("buyerPhone") val buyerPhone: String? = null,
    @SerializedName("shippingAddress") val shippingAddress: String? = null,
    @SerializedName("clinicName") val clinicName: String? = null,
    @SerializedName("createTime") val createTime: String? = null
)
