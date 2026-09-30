package com.chunbo.medical.util.qrcode

import android.graphics.Bitmap
import android.graphics.Color

object QrCodeBitmapHelper {

    /**
     * 根据输入文本生成标准的二维码 Bitmap 位图
     * @param content 二维码内容（如签到 URL 或签到令牌）
     * @param size 生成位图的宽高像素 (默认 360px)
     * @return 渲染完毕的 Bitmap
     */
    fun createQrBitmap(content: String, size: Int = 360): Bitmap {
        val qr = QrCode.encodeText(content, QrCode.Ecc.MEDIUM)
        val qrSize = qr.size
        val border = 2 // 留白边框格数
        val totalModules = qrSize + border * 2

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)

        for (y in 0 until size) {
            val moduleY = (y * totalModules) / size - border
            for (x in 0 until size) {
                val moduleX = (x * totalModules) / size - border
                val isDark = if (moduleX in 0 until qrSize && moduleY in 0 until qrSize) {
                    qr.getModule(moduleX, moduleY)
                } else {
                    false
                }
                pixels[y * size + x] = if (isDark) Color.parseColor("#0F172A") else Color.WHITE
            }
        }

        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }
}
