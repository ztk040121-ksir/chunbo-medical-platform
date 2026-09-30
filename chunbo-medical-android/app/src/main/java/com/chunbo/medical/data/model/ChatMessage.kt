package com.chunbo.medical.data.model

data class MallProductRecommendation(
    val id: Long = 0L,
    val productName: String,
    val specification: String = "",
    val price: String = "",
    val category: String = "",
    val csPitch: String = "",
    val imageUrl: String = ""
)

data class ChatMessage(
    val role: String, // "user", "assistant"
    var content: String,
    val time: String = "",
    val imageUri: String? = null,
    var quickReplies: List<String> = emptyList(),
    val isTriageCard: Boolean = false,
    val triageDept: String? = null,
    val triageLevel: String? = null,
    var processSteps: List<String> = emptyList(),
    var ragKnowledgeBases: List<String> = emptyList(),
    var mallProducts: List<MallProductRecommendation> = emptyList(),
    var rxItems: List<String> = emptyList(),
    var isStreaming: Boolean = false,
    var processDone: Boolean = false,
    var isProcessExpanded: Boolean = false
)
