# 阿里云 DoH

应用自行管理的 OkHttp / Retrofit 客户端在 `AhuHttp.plain()` 使用共享阿里云 DoH 解析器。Coil 默认图片加载器、RustSDK 的 Kotlin 热更新配置检查和校历下载也接入同一入口。

- 公共域名优先查询 `https://dns.alidns.com/dns-query`，同时请求 A / AAAA。
- 解析客户端独立于业务客户端，不带校园 Cookie、认证拦截器或业务日志。固定 bootstrap 地址 `223.5.5.5` / `223.6.6.6` 避免 DNS 递归；TLS 仍校验 `dns.alidns.com` 的证书与主机名。
- 单次 DoH 调用超时 5 秒，不跟随重定向；应用缓存目录下最多保存 2 MiB HTTP 缓存，缓存期限服从服务端响应头。
- IP 地址直接连接。`localhost`、单标签名称及本地域名使用系统 DNS。`ahu.edu.cn` 及其子域先检查校园 DNS：私有 IPv4、IPv6 ULA / 链路本地等结果保持校园解析；公共结果继续使用 DoH。
- DoH 失败或无地址时回退到系统 DNS，避免解析服务故障导致整个应用不可用。因此这是默认优先 DoH，不是阻断所有系统 DNS 的严格模式。

覆盖边界：WebView 使用 Chromium 的独立网络栈；Sentry、Bugly、广告 SDK 及预编译 Rust `.so` 内部自行发起的请求不受 OkHttp 的 DNS 配置控制。当前没有这些组件可共享的全局 DNS 接口，不宣称这些链路已经接入。设备的系统私有 DNS 或 VPN 配置也没有被修改。

验证任务：`:core:network:testDebugUnitTest` 覆盖公共域、校园私有地址、IPv6 ULA、IP / 本地域、故障回退，以及实际 DoH HTTP 失败响应的回退。应用 Debug 编译覆盖 Coil / Java Application 接入和 RustSDK 的 Kotlin 请求迁移；校园网络与设备 TLS / IPv6 路由仍需真机验证。

资料：阿里云[官方 DoH API 参考（公共 endpoint 和 bootstrap 地址）](https://static-aliyun-doc.oss-cn-hangzhou.aliyuncs.com/download%2Fpdf%2F171662%2FAPI_Reference_intl_en-US.pdf)、[当前接入配置说明](https://help.aliyun.com/zh/dns/httpdns-access-configuration)、[免费服务说明](https://help.aliyun.com/en/dns/free-service-disclaimer)；OkHttp [5.1.0 官方 DoH 实现](https://github.com/square/okhttp/blob/parent-5.1.0/okhttp-dnsoverhttps/src/main/kotlin/okhttp3/dnsoverhttps/DnsOverHttps.kt)；Coil [2.7.0 官方 singleton 配置入口](https://github.com/coil-kt/coil/blob/2.7.0/coil-singleton/src/main/java/coil/Coil.kt)。
