package com.ahu.ahutong.ui.screen.main

import android.webkit.WebView
import java.util.Locale
import kotlin.math.ceil

/** The official desktop UI has 872/1115/1164px minimum widths and disables mobile zoom. */
internal object StudentMailWebViewport {
    const val MIN_DESKTOP_WIDTH = 1280

    fun configure(view: WebView) {
        view.settings.apply {
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // Keep the installed WebView's browser version, but request the complete desktop client.
            userAgentString = userAgentString.replaceFirst(Regex("\\([^)]*\\)"), "(X11; Linux x86_64)")
                .replace(" Version/4.0", "").replace(" Mobile ", " ")
        }
        view.isHorizontalScrollBarEnabled = true
        view.isVerticalScrollBarEnabled = true
    }

    fun fit(view: WebView) {
        val width = view.width / view.resources.displayMetrics.density
        if (width > 0 && width.isFinite()) view.evaluateJavascript(script(width), null)
    }

    internal fun viewportContent(widthCss: Float): String {
        require(widthCss > 0 && widthCss.isFinite())
        val desktopWidth = maxOf(MIN_DESKTOP_WIDTH, ceil(widthCss.toDouble()).toInt())
        val scale = String.format(Locale.US, "%.6f", widthCss / desktopWidth)
        return "width=$desktopWidth,initial-scale=$scale,minimum-scale=0.1,maximum-scale=5,user-scalable=yes"
    }

    internal fun script(widthCss: Float): String {
        val content = viewportContent(widthCss)
        // This fixed script only adjusts the trusted top document's viewport; it never reads mail/session data.
        return """
            (function(){
              if(window.top!==window || location.protocol!=='https:' || location.hostname!=='mail.stu.ahu.edu.cn')return;
              var content='$content';
              var metas=document.querySelectorAll('meta[name="viewport"]');
              if(!metas.length){var meta=document.createElement('meta');meta.name='viewport';document.head.appendChild(meta);metas=[meta];}
              for(var i=0;i<metas.length;i++){if(metas[i].content!==content)metas[i].content=content;}
            })();
        """.trimIndent()
    }
}
