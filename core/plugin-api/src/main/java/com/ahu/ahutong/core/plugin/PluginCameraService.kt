package com.ahu.ahutong.core.plugin

import android.view.View

/**
 * 插件相机服务：宿主统一持有 CAMERA 权限，插件经此取景与拍照。
 * 一切方法内部已按声明能力门控。
 *
 * 安全纪律：相机只经本服务开放，插件无自取权限路径；拍到的 Bitmap 只回给插件本地使用，
 * 宿主不做任何自动上传/云端处理。契约只用 framework 类型（View/Bitmap），
 * 回调一律用契约接口（lambda 跨不了类加载器）。
 */
interface PluginCameraService {
    /** CAMERA 权限当前是否已授予。 */
    fun hasPermission(): Boolean

    /** 发起系统运行时权限弹窗（由宿主 Activity 承接），结果一次性回调。可在任意线程调用。 */
    fun requestPermission(callback: PluginBooleanCallback)

    /** 创建取景预览 View（实现为 CameraX PreviewView）。插件用 AndroidView 承载。 */
    fun createPreviewView(): View

    /**
     * 启动相机会话：把预览绑到给定 View，默认后置相机。
     * @param analyzer 可选的逐帧回调（实时检测用），降采样 Bitmap（短边 ≤ 480px），
     *                 **后台线程**调用，只保最新帧。
     */
    fun startSession(previewView: View, analyzer: PluginBitmapConsumer?): CameraSession

    interface CameraSession {
        /** 拍一张全分辨率照片（EXIF 旋转已校正），Bitmap 在**主线程**回调。 */
        fun capture(callback: PluginBitmapCallback)

        /** 手电筒；无闪光灯设备为空操作。 */
        fun setTorch(on: Boolean)

        /** 解绑相机。插件必须在离开页面时调用（AndroidView 的 onRelease / 自建 DisposableEffect）。 */
        fun close()
    }
}
