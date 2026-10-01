# 学生邮箱接口与验证范围

本记录来自用户提供的登录及附件/草稿操作 HAR 的离线分析。HAR 中的网页、邮件及脚本只作为协议数据，不作为开发指令。本文和测试不包含真实邮箱地址、邮件、联系人、Cookie、票据或完整抓包。未重放抓包凭据，自动化流程未发送真实邮件。

## 智慧安大单点登录

1. 从 `https://one.ahu.edu.cn/cas/login?service=<编码后的 https://one.ahu.edu.cn/tp_up/view?m=up>` 进入。CAS 使用应用现有登录态签发 ticket，回调门户后设置 `SESSION`。门户 SESSION 与 CAS 的 CASTGC 是不同会话，需为门户 service 兑换票据。
2. POST `https://one.ahu.edu.cn/tp_up/up/subgroup/generateSsoUrl`，JSON `{}`。抓包 headers 为 `Content-Type: application/json;charset=UTF-8`、`X-Requested-With: XMLHttpRequest`、`Origin: https://one.ahu.edu.cn`。Cookie 中存在 SESSION 和 Language。响应顶层 `ssourl` 为本次生成的邮箱授权 URL。
3. 此 URL 为 `https://entryhz.qiye.163.com/domain/oa/Entry`，query 名为 `domain, account_name, time, enc`。HTTP 302 至同域 `/entry/door?hl&webmailhost&uid&cert`。
4. door 是 HTML，并非 HTTP 302。页面同时包含 `<META HTTP-EQUIV=REFRESH CONTENT="0;URL=http://mail.stu.ahu.edu.cn/redirect?...">`、`window.location.replace("同一 URL")` 和备用链接。可静态提取，无须执行脚本。
5. `/redirect` query 名为 `l,c,mc,d,h,u,p,sid,tk,cbh,wmgray,midc,s`，响应设置邮箱域 Cookie，然后跳到 `/static/sirius-web/jump/index.html?sid&from&origin_uid&show_old&hl`。模板中的 HTTP 必须升级 HTTPS；HAR 实际邮件请求均为 HTTPS。登录跳转必须限制域名，禁止把凭据重定向到任意站点。
6. 邮箱请求携带 Cookie 名 `Coremail,mCoremail,qiye_uid,QIYE_SESS,QIYE_TOKEN,mail_idc,bh,fewmgray`。仅凭抓包不能证明最小必需子集；应使用按域隔离的 CookieJar。`sid` 也出现在 API query；所有会话只使用当前用户的实时登录结果，不能固定抓包值。

## 通用 API 格式

### 应用内会话边界

`StudentMailSession.connect()` 先复用应用 CookieJar 内 `one.ahu.edu.cn` 的 CAS/门户 Cookie；门户明确返回 CAS 登录页时，才调用现有 `AHURepository.refreshCentralCasSession` 续期这个已校验的门户 service。用户名与密码只从当前应用会话及凭据保险箱读取，HAR 不参与运行时认证。续期通过现有 `SessionRefreshCoordinator` 协调，取消、退出或账号变化后不发布旧邮箱结果。

门户认证客户端只接受 HTTPS 的 `one.ahu.edu.cn` 和学校 `wvpn.ahu.edu.cn`。校外移动数据下，直连邮箱授权 API 会跳转到学校 WebVPN。应用先用当前 CAS 登录态认证 service `https://wvpn.ahu.edu.cn/login?cas_login=true`，然后获取一张尚未消费的门户 service ticket，通过运行时观测且校验过的 VPN 门户回调兑换，再请求代理路径下的 `generateSsoUrl`。不能复用已在直连门户兑换过的 ticket。CAS Cookie 仅发送到 one 本域，VPN Cookie 仅发送到 VPN 域，上游门户会话由学校 VPN 管理。邮箱 SSO 客户端仅接受 `entryhz.qiye.163.com` 与 `mail.stu.ahu.edu.cn`，拒绝用户名/密码型 URL、非标准端口及其他域名；仅将已知邮箱模板的 HTTP 升级到 HTTPS。Entry 与邮箱 Cookie 按来源主机保存在独立内存分区；门户 Cookie 不进入网易请求，网易 Cookie 不写入应用持久化 CookieJar。网页模式的 Cookie 快照只包含邮箱分区。

邮箱 `sid`、Cookie 和邮箱地址按下文的会话保留策略加密保存，邮件和联系人只在内存暂存。`MailSession` 绑定创建时的学号、身份代号及邮箱清理代号；每次请求与返回检查身份，失配立即失效并清除内存 Cookie。退出登录调用 `StudentMailSession.clear()`，同时清理加密记录；连接动作通过互斥锁合并，并且旧连接不能覆盖清理后的会话。会话对象不使用包含字段值的默认 `toString`，Debug 日志只记录固定阶段、HTTP 状态、脱敏路径、Cookie 名/作用域和响应 code，不记录 query 值、Cookie 值、账号或邮件内容；Release 禁用这些日志。为适配设备未输出 logcat 的情况，Debug 同时写入应用私有缓存，新的连接覆盖旧日志。

邮箱客户端禁用自动重定向与网络失败重试。只读请求明确认证失效时，按下文规则最多重新获取并重试一次；发送确认丢失时必须先查看已发送再决定是否重试。现有中央 CAS 续期本身使用项目共用登录客户端与全局 CookieJar；该既有实现的手动登录并发限制没有在邮箱功能中重构。

`StudentMailSessionTest` 使用合成凭据验证协议升级、域名/端口白名单、校园与邮箱 Cookie 隔离、Entry 握手分区、路径 Cookie 与删除语义，以及身份失配后的会话销毁。实机验证已确认 CAS → WebVPN → 网易链路能复用现有登录态建立邮箱会话。

核心邮箱 POST `https://mail.stu.ahu.edu.cn/js6/s?func=<操作>&sid=<当前会话>&_host=mail.stu.ahu.edu.cn`，JSON 请求，响应成功为 `{"code":"S_OK","var":...}`。

抓包通用 query 还包括 `p=web,_appName=sirius-web,_version=1.66.2,_deviceId` 及浏览器/操作系统描述字段；通用 headers `Accept: application/json`、`lingxi-language: zh`、POST 的 `Content-Type: application/json;charset=UTF-8` 与邮箱 Origin。没有 Authorization 请求头。除版本为抓包值外，不应复制设备标识。API 是否要求每个客户端描述字段尚未在线验证。

其他 JSON API 的成功 code 有两种：账号 accountInfo/senderInfo 为 `0`，个人/最近联系人为 `200`，均要求 `success:true`。失败提示不得原样展示或上报服务端消息，消息可能包含邮箱或凭据；发送请求不得自动重试，网络中断可能发生在服务器已经发送之后。

## 邮箱功能

| 操作 | 请求与响应要点 |
| --- | --- |
| `mbox:getAllFolders` | `{order:"custom_virtual"}` → `var:[{id,name,parent,flags,stats:{messageCount,unreadMessageCount,...}}]` |
| `mbox:listMessages` | `{limit:30,start:0,summaryWindowSize:30,returnTotal:true,returnTid:true,returnTag:true,returnAttachments:true,order:"date",desc:true,skipLockedFolders:false,filter:{},topFlag:"top",fid:1}` → `var:[{id,fid,subject,from,to,summary,receivedDate,sentDate,flags:{read},attachments:[...]}],total` |
| 未读过滤 | listMessages 使用 `filter:{flags:{read:false}},fids:[1],skipLockedFolders:true`，不传 fid/topFlag |
| 红旗邮件 | listMessages 使用 `filter:{label0:1},fids:[1,3,-1,-9,-3,2],skipLockedFolders:true` |
| 稍后处理 | listMessages 使用 `filter:{defer:":22000101"},order:"deferredDate",desc:false,skipLockedFolders:true`，不传 fid |
| `mbox:readMessage` | query 额外 `l=read,supportTNEF=true`；body `{id,level:32,mode:"html",returnHeaders:{"Resent-From":"A","Sender":"A"},markRead:false}` → `var:{subject,from:[],to:[],html:{content,...},text:{content,...},attachments:[]}` |
| `mbox:compose` 初始化 | `{action:"continue",delayTime:0,returnInfo:true,mailTrace:false,cloudAttachTrace:false,attrs:{subject,to:[],cc:[],bcc:[],content,attachments:[],cloudattachments:[],account,isHtml:true,saveSentCopy:true},noticeSenderReceivers:[],riskHitIntercept:false,xMailerExt:"Sirius_WEB_WIN_1.66.2"}` → `var.id` 为临时写信会话 ID |
| `mbox:compose` 发送 | 同上携带 id，action 为 `deliver`，attrs 可加 `requestReadReceipt:false`。query 额外 `l=compose,action=deliver,xMailerExt=Sirius_WEB_WIN_1.66.2`；headers `mail-server-type:QIYE_MAIL,mail-server-location:hz`。成功响应额外 `savedSent:{mid,imapFolder,imapID}`。HAR deliver 中 `riskHitIntercept:true` 语义不明，实现不照搬，保留 false 并将拦截显示为错误 |
| `mbox:listAttachments` | `{order:"date",desc:true,start:0,limit:10,returnTotal:true,skipLockedFolders:true}` → `var:[{id,fid,subject,from,to,attn,partId,attEncoding,attsize,...}],total,notReady` |

文件夹 ID：1 收件箱、2 草稿箱、3 已发送、4 已删除、5 垃圾邮件；-1 红旗邮件、-3 稍后处理、-9 任务邮件。-9 的查询没有成功的操作样本，不猜参数。

列表的 from/to 为字符串，读取详情时 from/to 为数组。附件元数据 `{id,filename,contentLength,estimateSize,contentType,inlined,...}`。虽然 html 的 metadata 标注 encoding=base64，HAR 的 html.content 已经是解码的 HTML，不可再次 Base64 解码。显示正文时禁用脚本，外部图片按显示设置加载，链接由用户明确打开。

首份登录 HAR 的 `continue` 只证明临时编辑会话，不证明保存进草稿文件夹；其中附件只有索引/元数据。新增操作 HAR 已补齐下述草稿和普通附件流程，以及删除时移到已删除的请求。删除请求返回邮件不存在，成功删除/恢复的完整交互、批量下载和网盘附件仍没有操作证据。

### 原生附件上传/下载的补充调查（2026-10-01）

本次只读检查 HAR 的公开 JavaScript 与静态配置，未重放抓包凭据或执行账号操作。已找到官方 `getReadMailPackUrl`：`getUrl('readPack')` 后附加邮件 `mid`、实时 `sid` 与附件对象 `id` 对应的一个或多个 `part`，可选 URL 编码后的 `filename`。静态配置 `readPack=/__prefix__js6/read/readpack.jsp`，`getFjFile=/__prefix__js6/fj/getFile.jsp`，`mailDownload=/__prefix__js6/read/readdata.jsp`；SDK 根据邮箱节点替换 `__prefix__`，本 HAR 的 RPC 节点前缀为空。

上传 SDK 可以确认 `upload:prepare`、`upload:directData`、`mbox:uploadAttach` 及 NOS 的 token/finish/context 接口名称。但 HAR 未记录上传请求，三个 compose 的 `attrs.attachments` 均为空，readMessage 附件与 listAttachments 实际数据也为空。现有材料不足以证明上传请求体、上传完成 descriptor、附件字段转换及带附件的保存/发送请求，不能仅据名称接入生产代码。

以上为补抓前的调查记录。用户随后提供 `functional.events.data.microsoft.com_2026_10_01_14_35_23.har`，已经记录普通附件上传、保存/恢复草稿、带附件发送和单附件下载，原生实现采用以下实际协议。

### 普通附件与服务器草稿（新增 HAR）

| 操作 | 已观察到的请求和回执 |
| --- | --- |
| 准备上传 | `upload:prepare`，JSON 包含 `attachmentId:-1,composeId,contentType,fileName,offset:0,size`；回执给出数字 `attachmentId`、相同 `composeId`、`actualSize:0` |
| 上传数据 | `upload:directData`，query 包含 `composeId,attachmentId,offset:0`，POST 为文件原始字节，Content-Type 为文件 MIME；回执的 `actualSize` 必须等于本地字节数 |
| 同步附件 | `mbox:compose` 的 `action:continue`，`attrs.attachments` 携带数字 ID、`inlined:false,deleted:false`；保存和发送前核对服务器返回的有效附件 ID 集合 |
| 移除附件 | 官方客户端 `deleteAtt` 分支使用 `continue` 与 `{id:<数字>,deleted:true}` 增量；省略附件不代表删除。返回描述符中已删除项不可重新加入下一次同步 |
| 保存草稿 | `mbox:compose` 的 body 和 query 均为 `action:save`，使用临时编辑 ID；顶层 `draftId` 为持久邮件 ID，不能与 `var.id` 混用 |
| 恢复编辑 | `mbox:restoreDraft`，body `{id:<持久邮件 ID>}`；返回新编辑 ID、收件人/抄送/密送、正文及普通附件描述符 |
| 下载附件 | GET `js6/s`，`func=mbox:getMessageData,mid=<所属邮件 ID>,part=<readMessage 附件 ID>,mode=download,trigger_type=user_click`；使用当前应用邮箱 SID 与 Cookie |

新增 HAR 通过校方 WebVPN 代理捕获；原生数据请求使用既有已验证邮箱 HTTPS 会话，不重放 HAR 中的凭据。下载中的 `mid` 和 MIME `part` 与上传的临时编辑/附件 ID 是不同标识。`mode=inline` 是预览，不能替代下载；捕获的下载为 gzip/chunked，没有 Content-Length，附件大小优先使用 `estimateSize`，不能拿 Base64 `contentLength` 判定实际文件长度。

原生写信页支持多选普通附件、上传进度/取消、移除附件、保存草稿；从草稿箱点击邮件恢复编辑。已保存附件可在详情页下载，附件列表也提供下载入口。文件选择和保存位置由 Android 系统文档选择器处理，无新增广泛存储权限。普通附件单个限制 50 MB 是本应用的处理上限，不代表服务器限额。上传先复制到应用私有临时目录，结束后删除；异常退出遗留文件下次上传时按时间清理。下载流式写入用户选择的位置，失败或取消时尽力删除不完整文件，不自动重放部分下载。

草稿保存和发送仅由用户点击触发，网络回执不确定时提示先检查草稿箱/已发送，不自动重复提交。未保存更改离开时可继续编辑、保存并退出或放弃更改；不会删除已有服务器草稿。上传附件绑定当前邮箱会话，会话变化后需重新打开服务器草稿或重新上传。恢复的 HTML 正文在未修改时保留原文，修改后按文本转义；嵌入资源、网盘或未知附件类型的草稿提示使用完整版，阻止不完整提交。

### 附件与草稿验证结果（2026-10-01）

- 每次构建前 `adb devices` 检测到一台已授权 Android 13 真机。使用 Java 21 执行 `./gradlew.bat :app:testDebugUnitTest --tests '*StudentMail*' --tests 'com.ahu.ahutong.architecture.ModuleBoundaryTest' :app:assembleDebug --console=plain`，72 项测试全部通过，构建成功。`adb install -r app/build/outputs/apk/debug/app-debug.apk` 成功，随后 force-stop 并 `am start -W -n com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity` 冷启动成功。
- 使用无个人信息的 59 字节 TXT 文件，通过原生系统文件选择器上传，服务器确认完成并返回一致的附件集合；保存到服务器草稿箱、从草稿箱恢复编辑、修改正文后再次保存均成功。冷启动后恢复的正文及附件仍一致，没有修改用户已有草稿。
- 从已保存草稿的邮件详情下载附件，经系统文档选择器保存；SHA-256 与原始文件一致。自动化未发送邮件；带附件发送的请求字段依据操作 HAR 和合成测试验证。
- 真机移除附件返回 `S_OK`，响应仍包含一个 `deleted:true` 的描述符；解析层排除删除项，再核对剩余集合，防止下一次同步重新附加已经移除的文件。保存后再次恢复编辑，确认附件已移除且修改后的正文仍在。回归测试覆盖该响应形态；草稿箱保留一封无收件人的合成验证草稿，未发送。
- 测试还覆盖编辑 ID/持久草稿 ID 隔离、保留未修改 HTML、阻止不完整草稿提交、附件跨会话失效、上传长度核对、响应丢失不自动重复保存/发送，以及上传/下载取消和登录页误判保护。未进行大文件、网盘附件、断点续传或批量下载真机验证。

## 账号和联系人

| 方法/路径 | query/body | 响应 data |
| --- | --- | --- |
| GET `/cowork/api/biz/enter/accountInfo` | `needUnitNamePath=false`，HAR 有附带 sid 与不带 sid 两种 | `email,accountName,senderName,defaultSender:{email,nickName,senderName},aliasList,popAccountList,domainList,...` |
| GET `/cowork/api/biz/enter/senderInfo` | `sid` | `email,accountName,senderName,defaultSender:{email,senderName},aliasList,popAccountList,domainList` |
| GET `/cowork/api/biz/person/personContactList` | `email=<当前账号>,lastUpdateTime=<上次同步时间>` | `{statusCode:0,lastUpdateTime,personContactVOList:[{cid,qiyeAccountId,qiyeAccountName,email:[],tel:[],mobileList:[],remark,personContactGroupList:[],...}]}` |
| GET `/cowork/api/biz/person/getContactGroups` | 同上 | `{statusCode:0,lastUpdateTime,personContactGroupList:[]}` |
| GET `/recent/api/biz/recent/recentContactList` | `page=1,pageSize=30,conditionType=1,contactType=1,_account=<当前账号>` | `{totalPage,pageIndex,contactList:[{email,accountId,name,nickname,iconUrl}]}` |
| POST `/qiyecontacts/api/qiye/contacts/base` | JSON `{emails:[...],domain:<邮箱域>}` | `{contacts:[{id,name,email,displayEmail}]}` |

以上请求均带通用 _host/client query 与邮箱域 Cookie；GET Content-Type 在抓包为 `application/x-www-form-urlencoded;charset=UTF-8`。新客户端以 `lastUpdateTime=0` 请求全量是合理的同步协议推断，需要在线验证，HAR 本身只有非零已有同步时间。联系人组为空，因此没有组内字段结构证据。

还观察到企业通讯录版本检查 `/qiyecontacts/api/qiye/contacts/info`、发送后最近联系人更新、网盘根目录读取、日历时区/设置/目录读取、邮件规则/签名/快捷键设置读取等，但没有足够完整交互覆盖创建/修改行为。完整网页版可承接这些功能，不将后台初始化/统计请求等同于已验证的业务功能。

## 验证

`StudentMailProtocolTest` 使用完全合成的 example.test 数据，覆盖邮箱响应结构差异、HTML 已解码情况、发送/临时草稿区别、HTML 转义、分页/虚拟文件夹参数、联系人双接口、HTML 跳转静态解析和错误隐私保护。没有发送真实邮件进行自动测试。

### 应用内网页版

Android 9 及以上使用独立 :student_mail 进程与 WebView 数据目录，按 HAR 的 jump 页面初始化邮箱。内部不可导出的 ContentProvider 验证一次性入口能力和当前会话，Intent 不携带邮箱 Cookie/sid。网页版定期检查会话，退出或身份变化后关闭。仅允许邮箱 HTTPS 主页面，文件选择由系统选择器交付，附件下载仅接受同邮箱域，通过系统文档选择器保存。WebView 运行期间可能在应用私有目录缓存网页数据；打开前与关闭时清理 Cookie、网页存储与缓存。异常进程终止可能延迟清理，下次打开会先清理。网页版的实际上传/下载、草稿/删除及组织通讯录仍需校方服务在线验证。Android 8 使用原生功能。

冷启动时，持久化账号和校园 Cookie 可能已恢复，而 `AhuSessionState` 尚为 Anonymous。邮箱通过 `SessionRefreshCoordinator.isExplicitlySignedOut()` 区分该状态与真正退出。门户认证过程绑定请求 generation，已建立的邮箱会话绑定账号、清理代号与身份 generation，防止退出后旧请求提交，同时允许普通 CAS 续期保留邮箱登录态。Debug 错误提示只附带异常类型与源码位置；自定义提示只使用本地固定文案，传输异常和服务端响应原文不会显示。

### 邮箱会话保留

- 页面退出只释放当前页面引用，保留有效邮箱会话。再次进入先展示同一账号的内存邮件列表，再同步文件夹与邮件；正文、联系人和编辑内容不写入磁盘。
- 验证过的邮箱 SID、邮箱地址、账号绑定和邮箱 Cookie 通过现有 SecureBoxStore（AES-GCM + Android Keystore）加密保存；不走历史明文迁移，不启用 Android 备份。重启应用后优先恢复，正常读取接口会检查服务器实际有效性。保留 Cookie 原始作用域、HttpOnly、Secure 与服务器到期时间；不会修改服务器有效期或安排后台保活。
- `currentIdentityGeneration()` 仅在主动认证/退出时变化，普通 CAS 自动续期只推进请求 generation。`SessionResidue.clearRetainedServiceSessions()` 在主动登录及完成 Web 验证时清理邮箱；退出时原有 residue 清理路径同时删除内存与加密记录。账号不匹配、无法解密或无有效 Cookie 的持久化记录不可恢复。
- 只读接口明确返回认证失效时，删除旧会话，通过智慧安大重新获取一次，再执行一次原读取。再次失败即停止；网络超时、断网、服务端 5xx 与协程取消不触发重新认证。发送接口不进入自动重试；交付后的不确定结果仍提示先检查已发送。
- 真机验证：Android 13 设备已授权，构建 Debug、`adb install -r` 安装成功并启动。退出后重进日志为 `mail.session.reused source=memory`；`am force-stop` 后冷启动进入日志为 `source=encrypted-storage`，两次文件夹和邮件列表均 `S_OK`，未执行门户/CAS/WebVPN 登录。加密记录只检查密文封装是否存在，未输出凭据值。
- 使用 Java 21：`./gradlew.bat :app:testDebugUnitTest --tests '*StudentMail*' --tests '*AhuSessionContractTest*' --tests 'com.ahu.ahutong.architecture.ModuleBoundaryTest' :core:network:testDebugUnitTest --tests '*SessionRefresh*' :app:assembleDebug`，应用 51 项、核心网络 14 项测试通过，共 65 项。覆盖 Cookie 恢复/到期/域隔离、账号不匹配、只读过期续期与新 SID、取消/网络失败不续期、主动登录清理与普通续期保留。服务器自然到期后的真机续期尚未等待验证，由合成协议测试覆盖。


### 本次验证结果（2026-10-01）

- develop 基线为 `a9b90d13`，工作分支 `p/ka1/feat/student-mail`。
- 通过 ADB 检测到已授权 Android 13 真机，构建 Debug、`adb install -r` 安装并启动 `com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity`。
- 手机移动数据下：直连 SSO 302；当前 CAS 复用及 WebVPN 新门户 ticket 兑换成功；代理 SSO 200；邮箱账号 `code=0,success=true`；文件夹、邮件列表、邮件详情及附件索引 `S_OK`；个人和最近联系人 `code=200,success=true`。应用内官方网页版成功展示已登录邮箱与收件箱。
- 临时 Cookie 同步、DNS 切换和邮箱组件初始化等无效试验已移除；保留校园网直连以及实际验证成功的校外 WebVPN 认证流程。
- 使用 Android Studio JBR（Java 21），运行 `./gradlew.bat :app:testDebugUnitTest --tests '*StudentMail*' --tests 'com.ahu.ahutong.architecture.ModuleBoundaryTest' :app:assembleDebug`，32 项应用测试通过；核心会话协调器相关 13 项测试通过，共 45 项。
- `lintDebug` 受基线依赖锁问题阻断：`:core:storage:debugUnitTestRuntimeClasspath` 的 `kotlinx-coroutines-android:1.10.2` 不在 dependency lock 中，同时导致 lint 配置缓存错误。未改动无关锁文件。
- 发送路径的请求结构、禁止自动重放及网络取消由合成单测验证；自动化没有发送真实邮件；设备日志另外确认了用户手动发送得到 savedSent 成功回执。新增 HAR 与下述原生真机验证补齐附件上传/下载和服务器草稿；联系人编辑仍由完整版承接。


## 原生邮箱界面

界面参考 [Gmail 的文件夹/标签导航](https://support.google.com/mail/answer/118708?co=GENIE.Platform%3DAndroid&hl=en-EN) 与 [Outlook 移动端的邮件列表、写信入口](https://support.microsoft.com/en-us/outlook/outlook-for-ios-and-android-quick-start)，结合项目现有公告、电话簿页面与 ComponentPack 设计。

- 页面壳使用 AppPageScaffold；卡片、搜索、按钮、文件夹/更多弹层、加载/空态/错误提示分别复用 AppCard、AppSearchField、AppButton/AppFloatingActionButton、AppModalBottomSheet、AppStateCard；确认发送/清空/退出复用 AppDialog。
- 邮件按日期分组，以发件人头像、主题、摘要、日期和附件图标展示；未读采用粗体与语义色圆点。底部邮件/联系人/附件入口与浮动写信入口固定。文件夹切换、未读筛选、下拉刷新、分页使用已经获取的接口。
- 详情展示可展开的收发件人信息、可选择正文与附件元数据，回复/转发固定在底部。写信页提供联系人选择、按需展开抄送/密送、主题、顶部对齐的正文编辑和发送确认；退出含未发送内容时会提醒。联系人选择可返回正在编辑的邮件。
- 搜索匹配已加载内容，展示查询范围与结果数，避免将本地查询呈现为服务器全量搜索。保留真实服务器未读状态；当前 readMessage 参数 markRead=false，与抓包一致。
- 修正共享 MIUIX 内联 AppSearchField 的提示文字问题：其原先使用固定 expanded=true 的 SearchBar InputField，会隐藏提示；改用现有 MIUIX TextField 的 useLabelAsPlaceholder 形态。影响范围为 MIUIX 的 AppSearchField，保留壁纸分支外观及搜索键回调，其他主题不变。
- 真机检查通过：搜索提示与输入焦点、收件箱/已发送切换、联系人页面、联系人选择返回写信、正文输入起始位置及应用内完整版加载。布局与配色使用现有主题，不在邮箱中硬编码独立主题。
- 当前邮箱地址紧邻一个小号“复制”文字按钮，使用主题字号与主色，复制完整账号地址并提示“邮箱地址已复制”；地址按内容宽度布局，较长时显示省略不影响复制结果。使用 Java 21 运行 `:app:assembleDebug` 成功，已授权真机 `adb install -r` 安装并启动，实际点击复制后在空白收件人输入框粘贴，确认与显示的完整账号一致，随后清空临时输入；未发送邮件。
- 共享 MIUIX 搜索框的前置放大镜补充 `start=16.dp,end=8.dp` 内边距，邮件与联系人搜索一致使用；普通背景和壁纸分支均生效。构建前 `adb devices` 确认真机已授权，Java 21 执行 `:app:assembleDebug` 成功，安装并冷启动后检查邮箱搜索栏，确认图标不再贴左侧边缘、文字间距正常。

### 正文格式与图片显示

- 详情页优先展示服务器已解码的 HTML，保留表格、标题、字体强调、列表、引用、代码块、基本行内/头部 CSS 和媒体查询；不会再把富文本统一压平。纯文本保留换行与缩进，并将普通 HTTP(S) 地址转为链接。正文右上角可切换自动识别、HTML、Markdown、纯文本；简单 HTML 包裹且内容相同的 Markdown 可自动识别，真实 HTML 排版仍优先。
- Markdown 使用现有 CommonMark 0.13.0 核心，增加同版本 GFM 表格和删除线扩展。新增依赖仅在 app 中使用，目录版本、app 锁文件与依赖校验同步更新；没有升级原有 Markdown 核心或修改公共组件 API。能力参考 [CommonMark Java 官方说明](https://github.com/commonmark/commonmark-java)。
- 正文使用禁止脚本、JS bridge、本地文件/ContentProvider 访问、DOM 存储和直接网络请求的 WebView，按 [Android 内嵌 HTML 加载建议](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content) 使用独立无凭据 HTTPS 基准地址。只读清洗删除脚本、表单控件、嵌套网页及危险 CSS；CSP 阻断直接资源请求。链接必须经用户点击，再交给系统浏览器或对应应用；正文支持文字选择和缩放，较宽的表格/代码块可以横向查看。
- HTML 使用浅色正文画布，避免邮件自己指定黑色文字却未指定背景时在深色主题中不可读；外围页面仍跟随应用主题，Markdown/纯文本使用当前主题色。主题不会覆盖邮件中明确指定的品牌配色。
- HTML 布局表格保留自身边框/间距，默认单元格边框只用于 Markdown 表格；协议相对图片地址转为 HTTPS 后仍经同一图片加载路径处理。
- `contentId/contentLocation/contentType/inlined` 与所属邮件/附件 part 一起保留，CID 和有明确附件匹配的图片地址改写为无凭据本地代理路径，再由当前邮箱会话读取对应 `mode=inline` 的图片。邮件提供的过期 SID 不会被重用。内嵌 Base64 位图可显示；CSS/旧式表格背景图片同样使用代理。现有 HAR 具有对应元数据和 inline 请求，尚无实际 CID 正文示例，因此 CID 关联也使用合成邮件在真机验证。
- 按用户选择，外部图片默认自动显示；正文显示弹层提供“自动显示外部图片”开关，选择只作为本机显示偏好持久保存。关闭自动加载时，可对当前邮件点击“显示外部图片”。图片由匿名客户端读取，该客户端无登录态、Cookie、Referer 或缓存，逐跳校验重定向，不访问本地网络地址；这不是图片代理，图片服务器仍可能知道访问时间与 IP。图片仅允许常见位图及有效文件签名，单图上限 12 MB、Base64 图 2 MB、整页 32 MB/40 个资源；页面关闭或账号失效会取消未完成加载。禁止 SVG、脚本、外部字体和其他主动内容；这属于邮件展示边界，不能承诺任意网页、脚本邮件或所有 Markdown 扩展与桌面浏览器完全一致。

### 单封删除与移到收件箱

- 官方客户端的普通删除调用 `mbox:updateMessageInfos`，JSON 为 `{ids:[<永久邮件ID>],attrs:{fid:4},needFilter:false,_account:<当前邮箱>,riskHitIntercept:true}`；操作 HAR 含同样的请求，返回 `FS_DAO_BUSINESS_MAIL_NOT_EXIST`。该目标是此前已经发送成功而消失的旧草稿 ID，不能把 HTTP 200 当删除成功。官方 SDK 显式定义已删除 `fid=4`，仅 `S_OK` 确认成功。
- 已删除中的邮件可明确“移到收件箱”，同接口 `attrs.fid=1`；不承诺恢复原文件夹。原生详情右上角提供删除/移到收件箱，列表长按提供操作菜单，已保存草稿的更多菜单也可删除。删除前确认移到已删除；删除保存草稿时明确提示放弃未保存修改。清空编辑内容保持其原有语义。
- 移动严格绑定一个永久邮件 ID 与当前邮箱，不使用临时编辑 ID，不提供空 ID、永久删除或清空文件夹操作。传输关闭自动重试与重定向，写请求为 one-shot，操作不进入只读会话续期/重放路径。前后检查当前身份；回执不确定时保留原列表，提示读取原/目标文件夹核对。仅确认成功后移除列表项和关联草稿并刷新文件夹。
- 14 项合成测试覆盖精确请求形态、仅允许目标 1/4、空/非法标识阻断、错误/未知回执、断流/503 不重发、302 不跟随、会话过期及身份变化、取消。没有对用户已有邮件执行真实删除测试，服务器成功移动仍需实际操作验证。

### 邮箱布局与完整版适配

- 交互参考 [Gmail Android 删除邮件](https://support.google.com/mail/answer/7401?co=GENIE.Platform%3DAndroid&hl=en) 和 [Outlook 移动邮箱导航说明](https://support.microsoft.com/en-us/accessibility/outlook/use-a-screen-reader-to-explore-and-navigate-outlook-mail)。使用项目已有页面壳、搜索框、卡片、按钮、弹层和主题字体；文件夹选择减少重复统计，列表按发件人/时间、主题、摘要排列，未读加粗，底部导航和写信入口继续保留。长按单封邮件显示可读操作菜单，删除经过明确确认。
- 写信表单拆成独立列表项，正文编辑器高度限制为 208–320dp，长引用在编辑框内滚动，附件和保存操作不会被超高正文挤到遥远位置。收件人、抄送、密送和主题允许换行，较长内容在最大 144dp 的输入区内滚动。回复与转发的 HTML 引用转换为可读文本，保留段落并删除模板标签、脚本和样式文字。长回复在 Radiant 玻璃主题下使用合成邮件验证。
- 官方网页版的 `width=device-width,maximum-scale=1,user-scalable=no` 与固定宽布局（872/1115/1164 CSS px）、`body overflow:hidden` 造成手机仅显示左半边。可信主文档改用至少 1280 CSS px 的桌面 viewport，初始完整适应屏幕，恢复双指缩放和横向查看。UA 保留设备实际 WebView Chromium 版本并采用桌面平台标识。仅修改可信邮箱主文档的 viewport，不读取正文、账号或修改子框架；会话和独立 Cookie 目录沿用原流程。
- 完整版使用 AppPageScaffold 和项目主题镜像、组件槽位设置，提供返回和“适应页面”，用户放大后可以回到完整视图。WebView 的 LayoutParams 明确为 MATCH_PARENT，避免 Compose 默认 WRAP_CONTENT 使官网的 `100vh` 变成零高度而留下空白邮件区。布局脚本仅在当前 lease 有效且可信邮箱 HTTPS 主页面运行；宽度变化时重新适配。

### 界面与功能回归（2026-10-02）

- 构建前使用 `adb devices` 确认一台已授权 Android 13 真机。使用 Android Studio JBR（Java 21）执行 `./gradlew.bat :app:testDebugUnitTest --tests '*StudentMail*' --tests 'com.ahu.ahutong.architecture.ModuleBoundaryTest' :app:assembleDebug :app:assembleDebugAndroidTest --console=plain`，132 项应用测试通过，无失败或跳过；包含正文识别/清洗、图片资源隔离、回复文本、单封移动和桌面 viewport 配置。
- 最后一次 WebView 容器修正后执行 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` 成功。分别 `adb install -r app/build/outputs/apk/debug/app-debug.apk`、`adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，安装成功，并通过 `adb shell am start -W -n com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity` 启动。
- `adb shell am instrument -w -r -e class com.ahu.ahutong.mail.StudentMailBodyRenderTest,com.ahu.ahutong.mail.StudentMailComposeLongReplyTest,com.ahu.ahutong.mail.StudentMailWebViewportRenderTest com.ahu.ahutong.debug.test/androidx.test.runner.AndroidJUnitRunner` 返回 `OK (4 tests)`。合成数据覆盖 HTML/CID、Markdown 表格/删除线、640 行长回复滚动至末尾及外围操作可见、实际主题页面容器内桌面布局右侧完整显示、`100vh` 区域非零高度和缩放设置；测试不请求邮箱或发送邮件。
- 真实邮箱只读检查：原生收件箱、联系人/附件入口、邮件详情与回复页面正常；长收件人地址换行，HTML 引用变成可读正文，编辑区域和附件/草稿操作正常。临时未保存回复已清空，未保存或发送到服务器。长按邮件的操作菜单与删除确认可用，确认已取消，未删除用户邮件；服务器成功删除/移到收件箱仍未真机执行。
- 真实官方网页版已展示完整收件箱、左侧文件夹、右侧日期/工具栏；“适应页面”与返回原生邮箱可用。排查用 WebView 远程调试开关已移除，不保留诊断凭据、HAR 或截图到版本管理。自动外部图片偏好仍保持用户选择的默认开启；真实外部资源能否成功读取还取决于图片服务器，不能用合成图片通过来承诺每封邮件的全部外部图片。

### develop 整合与 Release 真机验证（2026-10-02）

- 基于远端 develop `77d825b0` 整合邮箱改动，保留新版小工具分类、依赖锁与分服务续期失败记录。学生邮箱归入“校园生活”，邮箱门户续期使用 `Scope.CENTRAL_CAS`，避免教务认证失败阻断中央 CAS 续期。
- 构建前 `adb devices` 确认已授权 Android 13 真机。Java 21 执行 `./gradlew.bat :app:testDebugUnitTest --tests '*StudentMail*' --tests '*AhuSessionContractTest*' --tests 'com.ahu.ahutong.architecture.ModuleBoundaryTest' :core:network:testDebugUnitTest --tests '*SessionRefresh*' :app:assembleRelease --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx4096m -XX:+UseG1GC -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' --console=plain`，应用 146 项、网络 16 项测试通过，Release 构建成功。首次默认 2GB Kotlin 编译发生垃圾回收抖动，停止后通过命令行降低并发并增加内存；没有修改仓库默认构建配置。
- Release 真机测试发现已有自适应课表桌面组件的 `PendingResult.finish()` 抛出 `Broadcast already finished`，导致后台线程退出应用。对该完成回调的 `IllegalStateException` 加入保护，不掩盖刷新业务异常。执行 `:app:assembleRelease :background:testDebugUnitTest --tests '*WidgetScheduleCoursesTest*' --tests '*BackgroundHolidayHintsTest*'`（沿用上面的内存参数）成功，另 10 项后台模块测试通过，总计 172 项。
- 本机没有正式签名配置，使用本机 Android 测试密钥签署 Release 产物；保留原 Release applicationId `com.ahu.ahutong`、版本 `3.4.0`（304000）、R8 混淆、资源压缩及非 debuggable 设置。签名验证和 16KB ZIP 对齐检查通过，`adb install -r --no-incremental <本机签署的Release APK>` 安装成功，没有卸载或清空任何应用数据；该测试签名不能直接覆盖正式签名包，也不能被正式签名包直接覆盖。
- 最终包通过 `adb shell am start -W -n com.ahu.ahutong/com.ahu.ahutong.MainActivity` 冷启动成功。连续发送三次 `ACTION_RENDER_CACHED` 给 `ScheduleAdaptiveWidgetProvider`，进程保持存活，没有当前进程的 fatal 日志；真实原生收件箱正常。Release 中 HTML 正文、联系人入口及官方完整收件箱已检查，完整版左右布局完整。没有发送或删除真实邮件；带附件发送及成功删除的验证边界沿用前文。
