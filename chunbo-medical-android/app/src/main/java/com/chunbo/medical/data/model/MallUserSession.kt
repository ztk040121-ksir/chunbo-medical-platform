package com.chunbo.medical.data.model

data class MallUserSession(
    val isLoggedIn: Boolean,
    val token: String,
    val username: String,
    val nickname: String,
    val avatar: String = "avatar_resident_1",
    val phone: String,
    val address: String,
    val balance: Double,
    val points: Int
)
