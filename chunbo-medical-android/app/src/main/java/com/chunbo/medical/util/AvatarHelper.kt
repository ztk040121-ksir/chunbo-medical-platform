package com.chunbo.medical.util

import android.content.Context
import android.widget.ImageView
import com.chunbo.medical.R

object AvatarHelper {

    val PRESET_AVATARS = listOf(
        PresetAvatar("avatar_resident_1", "健康居民 (翡翠绿)", R.drawable.avatar_resident_1),
        PresetAvatar("avatar_resident_2", "知性青年 (海天蓝)", R.drawable.avatar_resident_2),
        PresetAvatar("avatar_resident_3", "康养长者 (暖阳金)", R.drawable.avatar_resident_3),
        PresetAvatar("avatar_resident_4", "调理居民 (雅致紫)", R.drawable.avatar_resident_4),
        PresetAvatar("avatar_resident_5", "活力少年 (薄荷青)", R.drawable.avatar_resident_5),
        PresetAvatar("avatar_resident_6", "社区医生 (深蓝白褂)", R.drawable.avatar_resident_6)
    )

    data class PresetAvatar(
        val key: String,
        val label: String,
        val resId: Int
    )

    fun getDrawableRes(avatarKey: String?): Int {
        if (avatarKey.isNullOrBlank()) return R.drawable.avatar_resident_1
        return when (avatarKey) {
            "avatar_resident_1" -> R.drawable.avatar_resident_1
            "avatar_resident_2" -> R.drawable.avatar_resident_2
            "avatar_resident_3" -> R.drawable.avatar_resident_3
            "avatar_resident_4" -> R.drawable.avatar_resident_4
            "avatar_resident_5" -> R.drawable.avatar_resident_5
            "avatar_resident_6" -> R.drawable.avatar_resident_6
            else -> R.drawable.avatar_resident_1
        }
    }

    /**
     * 加载头像到指定的 ImageView（自动支持预设角色和真实上传的照片 URL）
     */
    fun loadAvatar(imageView: ImageView, avatarKey: String?) {
        if (avatarKey.isNullOrBlank()) {
            imageView.setImageResource(R.drawable.avatar_resident_1)
            return
        }
        if (avatarKey.startsWith("avatar_resident_")) {
            val resId = getDrawableRes(avatarKey)
            imageView.setImageResource(resId)
        } else {
            com.chunbo.medical.data.api.ImageLoader.loadImage(imageView, avatarKey, R.drawable.avatar_resident_1)
        }
    }
}
