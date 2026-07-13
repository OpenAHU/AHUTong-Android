package com.ahu.ahutong.ui.state

data class ApkDownloadSegment(
    val startFraction: Float,
    val endFraction: Float,
    val downloadedEndFraction: Float,
    val running: Boolean
)
