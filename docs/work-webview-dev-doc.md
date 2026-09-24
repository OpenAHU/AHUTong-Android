# 作业题目查看（WebView 方案）开发文档

> 状态：待开发 | 调研完成日期：2026-09-24 | 分支建议：`feat/work-webview`
> 本文件由调研 agent 基于真实页面抓样与风控实测撰写，主 agent 按本文实现，不要自由发挥网络层。

## 1. 需求与边界

用户在「学习通」页点击某条作业 → 弹出的作业详情对话框中点「查看题目」→ App 内 WebView 加载该作业的详情页，展示全部题目。

**硬边界（违反即重做）：**
- **只读**。不提交答案、不调用任何写接口、不提取/展示"正确答案"。
- **绝不预取、绝不批量**。只有用户点击的那一刻才加载一次详情页。
- 命中风控验证页时**熔断并明确提示**，不静默重试、不自动刷新。

## 2. 已验证的事实（调研结论，可直接采信）

| 事实 | 证据 |
|---|---|
| 作业列表项的 `data` 属性含完整详情 URL（含 workId/answerId/enc），已存于 `Work.detailUrl` | 真实抓样（workId=56097151 等 4 份） |
| 详情页 = 题目 + 「作答时间」同页，无需额外请求 | 同上 |
| 题目容器为 `div.questionLi#question{questionId}`，题号在 `h3.mark_name`（"1."），作答页题型在 `typeName` 属性 | 同上 |
| **未提交作业的页面不含"正确答案"**（实测 0 次出现） | 同上 |
| 已提交作业的"作业详情"页含 正确答案/我的答案（官方本就对学生可见） | 同上 |
| 风控是 **IP+会话级**，非账号级；同一 cookie 手机正常、被标记的 PC 必拦 | 对照实验（curl/浏览器/App） |
| 验证页特征：**HTTP 202** 或最终 URL 含 `antispiderShowVerify`，正文只有"提示页面/验证"无稳定标记 | 实测两次触发 |
| 课程列表响应自带 `开课时间：yyyy-MM-dd～yyyy-MM-dd`（节流入库字段 `Course.endTs` 已实现） | commit 8e1fd98 |

## 3. 实现方案

### 3.1 入口改动

- [WorkDetailDialog.kt](file:///C:/Users/InChange_Jiang/Documents/AHUTong-Android/feature/xuexiaotong/src/main/java/com/ahu/ahutong/ui/screen/xuexiaotong/WorkDetailDialog.kt)：对非自定义日程（`!isCustom`）且 `work.detailUrl` 非空的作业，在 `AppDialog` actions 里加「查看题目」按钮。
- 点击后导航到新屏幕 `WorkWebViewScreen(work: Work)`（或全屏 Dialog，按工程现有导航惯例，参考 [XuexiaotongScreen.kt](file:///C:/Users/InChange_Jiang/Documents/AHUTong-Android/feature/xuexiaotong/src/main/java/com/ahu/ahutong/ui/screen/xuexiaotong/XuexiaotongScreen.kt) L519 的 `selectedWork` 链路）。
- 按钮文案「查看题目」；自定义日程（`workId` 以 `event_` 开头）不显示该按钮。

### 3.2 WebView 屏幕（Compose + AndroidView）

```
feature/xuexiaotong/.../WorkWebViewScreen.kt  （新建）
```

配置要点：

```kotlin
WebView(context).apply {
    settings.javaScriptEnabled = true            // 页面渲染需要（UEditor 等），但我们不注入任何 JS 接口
    settings.domStorageEnabled = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW  // 题目图片是 http://p.ananas.chaoxing.com
    // 不要 addJavascriptInterface，不要 setSavePassword
}
```

- **User-Agent**：保持 WebView 默认（就是正常移动端 Chrome UA，最像真人）。**不要**改成 OkHttp 那个自定义 UA。
- **Referer**：`loadUrl(work.detailUrl, mapOf("Referer" to "https://mooc1.chaoxing.com/mooc2/work/list?courseId=${work.courseId}"))`——与 `fetchWorkDeadline` 的 Referer 策略一致（ChaoxingApi.kt L334 附近）。
- 返回键/标题栏：标题显示 `work.title`，提供关闭按钮。

### 3.3 Cookie 注入（登录态）

App 的超星 cookie 存于 `Store.getCookie()`（[Store.kt](file:///C:/Users/InChange_Jiang/Documents/AHUTong-Android/data/chaoxing/src/main/java/com/ahu/ahutong/data/xuexiaotong/Store.kt) L48），格式为单字符串：

```
name|domain=value; name2|domain2=value2; ...
```

注入步骤（在 `loadUrl` 之前完成）：

```kotlin
val cm = android.webkit.CookieManager.getInstance()
cm.setAcceptCookie(true)
Store.getCookie().split("; ").forEach { entry ->
    val name = entry.substringBefore("|")
    val domain = entry.substringAfter("|").substringBefore("=")
    val value = entry.substringAfter("=")
    // domain 形如 chaoxing.com（全局）或 mooc1.chaoxing.com（主机级），统一 set 到 https://<domain>
    cm.setCookie("https://$domain", "$name=$value")
}
cm.flush()
```

注意：`android.webkit.CookieManager` 与本工程自己的 `data.crawler.manager.CookieManager` 重名，import 时用全限定名避免引错。

### 3.4 风控熔断（必须实现）

`WebViewClient` 覆写：

```kotlin
override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = check(url)
override fun onPageFinished(view: WebView, url: String) = check(url)

private fun check(url: String) {
    when {
        url.contains("antispiderShowVerify") -> {
            // 命中风控：停止加载、退出页面、Toast「超星风控验证已触发，请过几小时再试」
            view.stopLoading(); onBlocked()
        }
        url.contains("passport2.chaoxing.com/login") -> {
            // 登录态失效：提示回学习通页重新登录
            view.stopLoading(); onSessionExpired()
        }
    }
}
```

- 302 重定向不会回调 `shouldOverrideUrlLoading`，所以检测必须放在 `onPageStarted`/`onPageFinished`（实测验证页最终 URL 即含 `antispiderShowVerify`）。
- 熔断后**不要**自动重试；用户手动重进才算新一次人类操作。

### 3.5 域白名单

`shouldOverrideUrlLoading` 中：仅放行 `*.chaoxing.com` 站内导航；其余（如智能分析 stat2-ans 外链、广告）交系统浏览器或直接拦截返回 true。页面内的作答交互（UEditor 输入）不拦截，但本功能不引导提交。

## 4. 风险纪律（给主 agent 的红线）

1. 本功能**不新增任何 OkHttp 请求路径**，只用 WebView 加载。
2. 不预取下一题/下一作业；不缓存 HTML 到磁盘（WebView 自己的 HTTP cache 可以开，属于正常浏览器行为）。
3. 不在后台线程批量打开详情页。一次只有一个 WebView 实例。
4. 分发版同样遵守：页面只读，答案区（已提交页的答案展示）属于官方页面原有内容，不做额外加工。

## 5. 验收清单（手动）

| 用例 | 预期 |
|---|---|
| 未交作业 → 查看题目 | 作答页打开，题目完整（含图片题干），无"正确答案" |
| 已提交作业 → 查看题目 | 作业详情页打开，题目与官方页面一致 |
| 图片题干（如复变作业） | 图片正常显示（mixed content 配置生效） |
| 断网 | WebView 报错页或自绘错误提示，不闪退 |
| 登录态失效（清空 cookie 后进入） | 提示重新登录，不白屏 |
| （模拟）命中验证页 | 熔断提示，不显示验证页内容，不自动重试 |
| 返回手势/按钮 | 正常退出，WebView 释放（onDestroy 里 `destroy()`） |

## 6. 参考样本

真实详情页 HTML 样本（调研时抓取）在本机 `%TEMP%\d_56097151.html`（作答页·简答题）、`%TEMP%\d_55512796.html`（详情页·填空题×18）。解析结构如需核对可读这两个文件，但注意它们是临时文件可能已被清理——优先以线上页面为准。

## 7. 不做清单（明确排除）

- 原生解析题目（已评估：富文本+图片题干成本爆炸，且解析不改动风控本质）
- 答案提取/自动作答/一键提交（红线）
- 作业列表页内置 WebView 化（保持现有原生列表）