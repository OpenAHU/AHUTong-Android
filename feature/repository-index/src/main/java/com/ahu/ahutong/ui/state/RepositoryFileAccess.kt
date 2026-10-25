package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.repository.DownloadedFile

/**
 * 本地文件的读取与"交给系统查看器打开"。
 *
 * 这两件事都要 Context、FileProvider 与 Intent，属于框架能力；收成接口之后，
 * ViewModel 就只依赖"读到了什么 / 打开成没成功"，可以在 JVM 单测里被 fake 驱动。
 * 生产实现是 :app 的 AndroidRepositoryFileAccess。
 */
interface RepositoryFileAccess {

    /** 系统目录选择器的初始位置；没有自选目录时为 null。 */
    fun downloadDirectoryUri(): String? = null

    /** 保存用户通过系统选择器授予的持久读写权限，并验证目录可写。 */
    fun rememberDownloadDirectory(uri: String) {
        throw SecurityException("无法保存下载目录授权")
    }

    /** 重新授权后，在所选目录中重新定位原文件；不隐式重新下载。 */
    fun recoverDownloadedFile(file: DownloadedFile): DownloadedFile? = file

    /** 读取文件内容（内容 URI 或本地路径）。 */
    fun read(file: DownloadedFile): FileReadOutcome

    /** 用系统查看器打开。失败与"没有可用应用"都作为结果返回，不弹提示。 */
    fun open(file: DownloadedFile): FileOpenOutcome
}

sealed interface FileReadOutcome {

    data class Text(val content: String) : FileReadOutcome

    /** 已确认文件被删除；失去读取权限不属于 Missing。 */
    data object Missing : FileReadOutcome

    data object PermissionRequired : FileReadOutcome

    data class Failed(val message: String?) : FileReadOutcome
}

sealed interface FileOpenOutcome {

    data object Opened : FileOpenOutcome

    data object Missing : FileOpenOutcome

    data object PermissionRequired : FileOpenOutcome

    data object NoViewerApp : FileOpenOutcome

    data class Failed(val message: String?) : FileOpenOutcome
}
