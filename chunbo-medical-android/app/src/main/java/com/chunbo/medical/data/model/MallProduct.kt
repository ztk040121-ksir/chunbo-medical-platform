package com.chunbo.medical.data.model

import com.google.gson.annotations.SerializedName

data class MallProduct(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("productName") val productName: String? = null,
    @SerializedName("genericName") val genericName: String? = null,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("specification") val specification: String? = null,
    @SerializedName("manufacturer") val manufacturer: String? = null,
    @SerializedName("price") val price: Double? = null,
    @SerializedName("retailGuidePrice") val retailGuidePrice: Double? = null,
    @SerializedName("wholesalePrice") val wholesalePrice: Double? = null,
    @SerializedName("stock") val stock: Int? = null,
    @SerializedName("stockQty") val stockQty: Int? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("suitableSymptoms") val suitableSymptoms: String? = null,
    @SerializedName("csPitch") val csPitch: String? = null,
    @SerializedName("directorPitch") val directorPitch: String? = null,
    @SerializedName("buyerPitch") val buyerPitch: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null
) {
    val displayName: String
        get() = when {
            !productName.isNullOrBlank() -> productName
            !genericName.isNullOrBlank() -> genericName
            else -> "春播健康药品"
        }

    val displayPrice: Double
        get() = retailGuidePrice ?: price ?: wholesalePrice ?: 0.0

    val displayStock: Int
        get() = stock ?: stockQty ?: 0

    val displayCategory: String
        get() = category ?: "家庭常备"

    /** 卖点 / 适应症：优先取后端真实字段（csPitch/买点/店主话术），兼容旧字段 */
    val displayPitch: String
        get() = csPitch ?: suitableSymptoms ?: buyerPitch ?: directorPitch ?: description ?: ""

    /** 用于搜索/筛选的完整可检索文本（真实字段聚合，不再依赖不存在的 suitableSymptoms） */
    val searchableText: String
        get() = listOfNotNull(
            productName, genericName, brand, category, specification, manufacturer,
            csPitch, directorPitch, buyerPitch, suitableSymptoms, description
        ).joinToString(" ")
}
