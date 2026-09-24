# 安大通小工具插件开发规范 v0.1（草案 · 数据驱动声明式插件）

> 状态：草案，待评审
> 适用范围：安大通 Android 客户端 · 小工具页插件（方案 A：声明式，无可执行代码）
> 读者：插件开发者

---

## 1. 这是什么

安大通小工具插件是一个 **`.ahutool` 数据包**：一份 `manifest.json` 清单 + 一个图标文件，打成 zip 改后缀即可。

**插件里没有代码。** 你声明三件事：

1. **数据从哪来**（一个 HTTPS 接口 + 字段提取规则）
2. **长什么样**（从内置渲染模板中选一个）
3. **叫什么、什么图标**（入口信息）

宿主 App 负责请求数据、用安大通原生组件渲染、处理主题/亮暗/玻璃质感。**你写的每一个像素都会自动和安大通 UI 一致——因为你根本不写像素。**

### 1.1 能做什么 / 不能做什么

| ✅ 适合 | ❌ 不适合 |
|---|---|
| 信息查询类工具（列表/详情/键值展示） | 需要复杂交互逻辑的工具 |
| 表单提交类（报修、报名、问卷） | 需要后台定时任务 |
| 数据图表（趋势/占比） | 需要访问学校账号数据的（插件永远拿不到登录态） |
| 内容聚合（公告/帖子/失物招领） | 需要操作本地文件/蓝牙/传感器 |

### 1.2 安全模型（开发者须知）

- 插件**没有任何可执行代码**，宿主只解析数据
- 网络请求必须 **HTTPS** 且域名在 manifest 中**预先声明**；未声明的域名一律拦截
- 插件请求**不携带**安大通的登录态、Cookie、token（独立网络栈）
- 插件私有 KV 存储相互隔离，读不到宿主和其他插件的任何数据

---

## 2. 插件包结构

```
my-tool.ahutool          # zip 格式，改后缀
├── manifest.json        # 必需：插件清单
└── icon.png             # 必需：入口图标（建议 192×192，PNG 透明底）
```

图标建议：**单色线性风格**（iconpark 风格），最终会被宿主着色（tint）以匹配主题——提供纯白或纯黑的单色图最佳，彩色图标会被强制单色化。

---

## 3. manifest.json 完整规范

### 3.1 顶层结构

```json
{
  "specVersion": 1,
  "id": "openahu.campus-wall",
  "name": "校园墙",
  "version": 3,
  "author": "你的名字或团队",
  "description": "浏览校园墙最新帖子",
  "minHostVersion": "3.5.0",
  "permissions": {
    "network": ["wall.example.com"]
  },
  "entry": { "…见 §4 渲染模板…" }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `specVersion` | int | ✅ | 清单规范版本，当前固定 `1` |
| `id` | string | ✅ | 反向域名式唯一 ID，装载后不可变（更新靠它识别） |
| `name` | string | ✅ | 入口显示名，≤8 个汉字 |
| `version` | int | ✅ | 插件自身版本号，**每次发布递增** |
| `author` | string | ✅ | 展示在插件详情页 |
| `description` | string | ✅ | 一句话说明，展示在装载确认页 |
| `minHostVersion` | string | ❌ | 最低宿主版本，不满足则拒绝装载并提示 |
| `permissions.network` | string[] | 视情况 | 需要联网时必填：域名白名单（精确域名，不含协议/路径） |
| `entry` | object | ✅ | 页面定义，见 §4 |

### 3.2 网络声明（permissions.network）

```json
"permissions": {
  "network": ["api.example.com", "cdn.example.com"]
}
```

- 只写**域名**（不含 `https://`、不含路径）
- 不支持通配符；子域名需逐个列出
- 装载确认页会向用户展示：「该插件将访问：api.example.com」
- `source.url` 的域名必须在列表内，否则装载时校验失败

---

## 4. 渲染模板（entry）

`entry.type` 选择模板，其余字段按模板 schema 填写。**这就是全部的「UI 开发」。**

### 4.1 通用：数据源定义（source）

除 `markdown` 与 `link` 模板外，所有模板都有 `source`：

```json
"source": {
  "url": "https://wall.example.com/api/posts",
  "method": "GET",
  "headers": { "X-Api-Key": "xxx" },
  "refreshIntervalSec": 300
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `url` | ✅ | HTTPS；域名必须在白名单内 |
| `method` | ❌ | `GET`（默认）/ `POST` |
| `headers` | ❌ | 自定义请求头 |
| `body` | ❌ | POST 时的 JSON body |
| `refreshIntervalSec` | ❌ | 页面打开时的静默刷新间隔，默认 60，最小 30 |

### 4.2 字段提取（JSONPath 子集）

凡需要从响应里取数据的地方，用简化 JSONPath：

- `$.items` — 数组
- `$.items[].title` — 数组每项的字段
- `$.data.stats.total` — 嵌套字段
- 不支持过滤器、递归下降、脚本表达式

响应必须是 JSON。非 2xx / 非 JSON / 路径不存在 → 宿主显示标准错误卡（可重试），插件无需处理。

### 4.3 模板一：`list`（列表页，最常用）

```json
"entry": {
  "type": "list",
  "title": "校园墙",
  "source": { "url": "https://wall.example.com/api/posts" },
  "list": {
    "items": "$.posts",
    "title": "$.posts[].content",
    "subtitle": "$.posts[].author",
    "caption": "$.posts[].time",
    "badge": "$.posts[].tag"
  },
  "onItemClick": {
    "type": "detail",
    "title": "帖子详情",
    "markdown": "$.posts[].fullText"
  }
}
```

- `items` 指向数组；其余字段以同一路径取每项字段（宿主按索引对齐）
- `badge` 显示为右侧胶囊（主题色）
- `onItemClick` 可选：`detail`（MD 详情页）/ `url`（外部浏览器打开 `$.posts[].link` 字段）
- 渲染形态：安大通标准列表卡（与账单页同一组件），自动继承主题/亮暗/玻璃

### 4.4 模板二：`kv`（键值卡片）

```json
"entry": {
  "type": "kv",
  "title": "校车时刻",
  "source": { "url": "https://bus.example.com/api/today" },
  "kv": {
    "items": [
      { "label": "下一班", "value": "$.next.time" },
      { "label": "起点站", "value": "$.next.from" },
      { "label": "剩余座位", "value": "$.next.seats" }
    ]
  }
}
```

适合「几个关键数字」的场景。渲染为居中大数字 + 标签的 KPI 卡片（同统计页汇总卡组件）。

### 4.5 模板三：`grid`（网格入口）

```json
"entry": {
  "type": "grid",
  "title": "部门电话",
  "source": { "url": "https://example.com/api/contacts" },
  "grid": {
    "items": "$.departments",
    "title": "$.departments[].name",
    "iconText": "$.departments[].abbr",
    "onItemClick": { "type": "url", "url": "$.departments[].page" }
  }
}
```

2-3 列网格，每项显示缩写圆标 + 名称。适合多入口聚合。

### 4.6 模板四：`chart`（趋势图）

```json
"entry": {
  "type": "chart",
  "title": "图书馆人流",
  "source": { "url": "https://lib.example.com/api/flow" },
  "chart": {
    "kind": "line",
    "points": "$.hourly",
    "x": "$.hourly[].hour",
    "y": "$.hourly[].count"
  }
}
```

`kind` 当前仅支持 `line`（平滑折线，同账单统计页组件）。后续版本可加 `bar`。

### 4.7 模板五：`markdown`（纯内容页）

```json
"entry": {
  "type": "markdown",
  "title": "社团章程",
  "markdownUrl": "https://club.example.com/charter.md"
}
```

或内联：

```json
"entry": {
  "type": "markdown",
  "title": "使用须知",
  "markdown": "# 须知\n\n- 第一条\n- 第二条"
}
```

用宿主内置 MD 渲染器（与隐私政策页同一套，支持标题/粗体/列表/表格/链接）。`markdownUrl` 与 `markdown` 二选一。

### 4.8 模板六：`form`（表单提交）

```json
"entry": {
  "type": "form",
  "title": "设备报修",
  "form": {
    "submit": { "url": "https://fix.example.com/api/report", "method": "POST" },
    "fields": [
      { "key": "location", "label": "地点", "kind": "text", "required": true },
      { "key": "type", "label": "类型", "kind": "select", "options": ["网络", "水电", "门窗"] },
      { "key": "detail", "label": "描述", "kind": "multiline", "required": true }
    ],
    "successMessage": "已提交，感谢反馈"
  }
}
```

- `kind`：`text` / `multiline` / `select`
- 提交体为 `{ "location": "...", "type": "...", "detail": "..." }`
- 成功 → successMessage toast 并返回；失败 → 标准错误提示
- 输入校验只有 `required`，无格式校验（v1 克制范围）

### 4.9 入口角标（badge，可选）

任何模板可附加：

```json
"badge": {
  "source": { "url": "https://wall.example.com/api/unread" },
  "value": "$.count",
  "hideWhenZero": true
}
```

小工具页入口图标右上角显示数字角标。角标数据在小工具页打开时刷新，不后台轮询。

---

## 5. UI 一致性是怎么保证的（重要认知）

**插件开发者没有、也不需要有 UI 组件调用能力。** 一致性由三件事结构性保证：

1. **渲染全部走宿主组件**：列表 = 安大通列表卡；折线 = 统计页折线；MD = 隐私政策渲染器。主题色、亮暗切换、玻璃质感、圆角、间距全部是工程 token，不随插件变化
2. **模板即组件契约**：你能选的只有「哪种页面形态 + 数据字段映射」，没有颜色/字号/边距参数——v1 刻意不提供任何视觉自定义字段
3. **图标强制单色化**：入口图标统一 tint，与内置小工具视觉完全同构

**给你的唯一 UI 建议**：写好你的 `name` 和字段文案——那是你唯一拥有的「界面」。

---

## 6. 装载、更新与卸载

### 6.1 侧载流程

1. 用户获得 `.ahutool` 文件（QQ/网盘/扫码，任何渠道）
2. 系统分享 → 安大通「打开」→ 进入**装载确认页**，展示：
   - 名称、作者、描述、版本
   - **将访问的域名列表**（醒目位置）
   - 「插件无法访问你的校园账号数据」固定说明
3. 用户点「添加」→ 校验（schema/域名/版本）→ 出现在小工具页

### 6.2 更新

同 `id` 且 `version` 更大 → 覆盖更新（保留私有 KV）；`version` 不增 → 拒绝并提示。

### 6.3 卸载

小工具页长按插件 → 移除。删除入口 + 私有 KV，无残留。

### 6.4 校验失败一览（开发者自检表）

| 失败原因 | 提示 |
|---|---|
| manifest 不是合法 JSON / 缺必填字段 | 「插件清单格式不正确」 |
| `specVersion` 高于宿主支持 | 「请升级安大通后装载」 |
| `source.url` 域名未声明 | 「插件声明的域名与实际请求不符」 |
| 非 HTTPS | 「插件数据源必须使用 HTTPS」 |
| `minHostVersion` 不满足 | 「需要安大通 x.y.z 及以上版本」 |
| `entry.type` 未知 | 「宿主不支持该页面模板」 |

---

## 7. 版本演进机制

- `specVersion`：清单结构变更时递增（当前 1）。宿主拒绝更高版本，提示升级 App——与隐私政策版本机制同款
- 模板新增（如 `bar` 图表）只增不减，旧清单永远可用
- 字段弃用提前一个 specVersion 公告

---

## 8. 完整示例：校园墙插件

```
campus-wall.ahutool
├── manifest.json
└── icon.png
```

```json
{
  "specVersion": 1,
  "id": "openahu.campus-wall",
  "name": "校园墙",
  "version": 1,
  "author": "OpenAHU 社区",
  "description": "浏览校园墙最新帖子与热榜",
  "permissions": { "network": ["wall.example.com"] },
  "entry": {
    "type": "list",
    "title": "校园墙",
    "source": {
      "url": "https://wall.example.com/api/posts?sort=latest",
      "refreshIntervalSec": 120
    },
    "list": {
      "items": "$.posts",
      "title": "$.posts[].content",
      "subtitle": "$.posts[].author",
      "caption": "$.posts[].time",
      "badge": "$.posts[].tag"
    },
    "onItemClick": {
      "type": "detail",
      "title": "帖子详情",
      "markdown": "$.posts[].fullText"
    }
  },
  "badge": {
    "source": { "url": "https://wall.example.com/api/unread" },
    "value": "$.count",
    "hideWhenZero": true
  }
}
```

**前提**：校园墙有开放 Web API（见注意事项）。若目标墙只有微信小程序形态而无 Web 数据出口，请先与运营方沟通获取只读接口——逆向小程序接口的插件不会被接受。

---

## 9. FAQ

**Q：我的接口需要鉴权怎么办？**
A：在 `source.headers` 写静态 token。动态鉴权（OAuth/签名）v1 不支持——这类场景说明数据源不是为开放生态设计的，建议推动运营方提供只读公开接口。

**Q：能分页加载吗？**
A：v1 单页全量。数据源应按「一屏可读」设计返回量（建议 ≤50 条）。分页在 specVersion 2 规划内。

**Q：能不能加一个我自己的模板？**
A：模板由宿主内置。有好形态请提 issue 进官方模板库——这也是保证全插件视觉统一的机制。

**Q：插件会被审核吗？**
A：本规范约束的是侧载插件的技术边界，不构成内容审核承诺。宿主保留对「仿冒学校名义、诱导输入校园账号密码」类插件的处置说明义务（装载确认页固定声明插件无权获取账号数据）。

**Q：私有 KV 怎么用？**
A：v1 的声明式模板没有暴露 KV（没有可执行逻辑就没有写入方）。KV 为 specVersion 2（脚本能力）预留。

---

## 10. 给评审者的开放问题（v0.1 → v1.0 前需拍板）

1. `form` 模板是否进 v1（提交类插件有刷接口风险，可能需要限流声明）
2. 角标 `badge` 是否进 v1（多一次请求/页面打开）
3. 是否要求插件包内附 `LICENSE` 文本（开源生态约定）
4. 装载确认页是否需要「开发者实名/联系方式」字段（校园社区自治考量）
