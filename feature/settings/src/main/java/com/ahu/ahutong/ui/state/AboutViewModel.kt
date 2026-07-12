package com.ahu.ahutong.ui.state

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {
    val versionName: String? by lazy {
        val app = getApplication<Application>()
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
        packageInfo.versionName
    }

    val tipState = mutableStateOf<String?>(null)
}
