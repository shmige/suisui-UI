package com.svip.mansui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

lateinit var templateApp: App

class App : Application(), ViewModelStoreOwner {

    private val appViewModelStore by lazy { ViewModelStore() }

    override fun onCreate() {
        super.onCreate()
        templateApp = this
        // 版本比对基准 (主页「应用版本」行与更新弹窗都用它)
        AppUpdate.currentVersion = BuildConfig.VERSION_NAME
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}
