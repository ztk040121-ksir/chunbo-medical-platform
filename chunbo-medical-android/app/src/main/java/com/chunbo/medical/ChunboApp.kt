package com.chunbo.medical

import android.app.Application
import com.chunbo.medical.data.api.ApiClient
import com.chunbo.medical.data.api.UserManager
import com.chunbo.medical.ui.mall.CartManager

class ChunboApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
        UserManager.init(this)
        CartManager.init(this)
    }
}
