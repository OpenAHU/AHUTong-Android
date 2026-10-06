# 安大必吃榜 · 服务端接口对接文档

> 面向：客户端（AHUTong App）开发侧
> 服务端状态：**已开发完成并部署上线**（2026-10-06）
> 服务端代码：服务器 `~/projects/ahutong/`（本机开发副本在 Qoder 工作区 `ahutong-server/`）

---

## 0. 一句话

客户端上传 **「哪台 POS、哪一秒、多少钱」** 的**去标识交易**；服务端负责去重、聚合与全部分析，只下发**算好的结果**。

> **关键**：上传的数据里**没有任何用户 / 设备标识**（连哈希都没有）。
> 去重靠 **`(POS, 秒, 金额)`** 唯一约束——同一台 POS 同一秒不会刷出两笔**同金额**交易，所以同一笔交易无论被上传多少次、被多少台设备上传，都只落一行。

---

## 1. 服务地址

| 环境 | Base URL | 说明 |
|---|---|---|
| **现在（备案前）** | `http://121.37.174.199:8000` | 也可用 `http://121.37.174.199`（80 口同样可用）。**HTTP 明文** |
| 备案 + 域名就绪后 | `https://<你的域名>` | 服务端只改一行配置即自动启用 HTTPS，**接口路径不变** |

> ⚠️ **客户端必须注意**：当前是 **HTTP 明文**，Android 9(API 28)+ 默认禁止明文流量。联调阶段需在 App 里放行该地址：
> - 全局放行：`AndroidManifest.xml` → `<application android:usesCleartextTraffic="true">`
> - 或精确白名单（推荐）：`network_security_config.xml` 里为 `121.37.174.199` 开 `cleartextTrafficPermitted="true"`
> 上线换 HTTPS 后应把这些放行**撤掉**。

**连通性自检**：`curl http://121.37.174.199:8000/api/health` → `{"ok":true}`
**交互式文档**：`http://121.37.174.199:8000/docs`

> 📦 服务端已灌入一组演示数据（约 400 笔交易 / 6 个窗口 / 3 天），方便直接联调看真实响应。
> 清掉：`cd ~/projects/ahutong/deploy && docker compose stop api && rm -f ../data/app.db* && docker compose start api`

---

## 2. 通用约定

| 项 | 约定 |
|---|---|
| 编码 | UTF-8 |
| JSON 字段风格 | **camelCase**（如 `terminal`、`amountCents`、`minTxns`） |
| 请求体 | `Content-Type: application/json` |
| 成功无内容 | **204**，无响应体 |
| 错误 | `{"detail": "..."}` + 状态码 |
| 鉴权 | 读取接口无需鉴权；`/api/admin/*` 与 `/admin` 走 HTTP Basic（仅管理员）。
**写入接口**可选要求 `X-Api-Key`（服务端设了 `WRITE_API_KEY` 才校验） |
| 时间格式 | 上传用 `YYYY-MM-DD HH:MM:SS`（本地时间，秒级）；返回 ISO 字符串 |

**错误码**

| 码 | 含义 | 处理 |
|---|---|---|
| 204 | 成功 | 视为成功 |
| 401 | 写入密钥不对 | 检查 `X-Api-Key` |
| 422 | 参数不合法（含整批校验失败） | 修正参数，不要重试 |
| **429** | 限频（同 IP 写 ≤30 次/分、读 ≤180 次/分） | **退避后重试** |

---

## 3. 窗口映射表（人工维护）

### 3.1 拉取

```
GET /api/canteen/window-map?sinceVersion={int}
```

| 参数 | 说明 |
|---|---|
| `sinceVersion` | 客户端本地版本号。**与当前版本相同 → 返回空 `entries`**；不传 → 全量 |

**响应 200**

```json
{
  "version": 1,
  "entries": [
    { "terminal": "88-001", "name": "石锅拌饭", "canteen": "榴园", "floor": null }
  ]
}
```

**客户端语义**：首次全量落地并保存 `version`；之后带 `version` 请求，版本未变则沿用缓存；变了则返回**全量新表**（非差分），**整体替换**；**离线必须回退本地缓存**。

### 3.2 上报窗口名（可选功能，冷启动期不启用）

```
POST /api/canteen/window-report
Header: X-Reporter-Token: <匿名UUID，可选>
Body: { "terminal": "62-118", "merchant": "北二区食堂一楼", "suggestedName": "黄焖鸡", "sampleCount": 3 }
→ 204
```

- 只进**待审队列**，管理员审核 + 点「发布」后才进映射表
- `X-Reporter-Token` 仅用于同人去重与限频，服务端只存**哈希**；不传也可

---

## 4. 交易上传与分析结果

### 4.1 上传去标识交易

```
POST /api/canteen/txns
Body:
{
  "txns": [
    { "terminal": "88-001", "ts": "2026-10-06 12:03:05", "amountCents": 1300, "canteen": "榴园" }
  ]
}
→ 204
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `terminal` | ✅ | POS 终端码（账单 `locationName`，如 `77-139`） |
| `ts` | ✅ | **`YYYY-MM-DD HH:MM:SS`**（本地时间，**秒级**）。容忍 `T` 分隔符；时间越界（如 `99:99:99`）会被拒 |
| `amountCents` | ✅ | **必填**，本笔金额（分）。它参与幂等键 `(POS, 秒, 金额)`，**缺了会让同一笔重复入库** |
| `canteen` | 否 | 食堂名（客户端本地映射，如「北区二食堂」→「榴园」）。用于食堂榜，**管理员无需手填** |

**服务端行为**

| | |
|---|---|
| **去重** | 唯一约束 **`(terminal, ts, amount_cents)`** + `INSERT OR IGNORE`。**同一笔反复上传只算一次**，无需客户端做任何同步 |
| **正餐口径** | 只统计**午餐 10:30–14:00、晚餐 16:30–22:30**；早餐/夜宵不计入榜单（服务端过滤，客户端**可以全量上传**） |
| **脏数据防护** | 时间越界/非法拒收；单笔金额 >500 元丢弃；每台 POS 每天 ≤3000 笔；终端总数 ≤5000 |
| **限频** | 同 IP 写 ≤30 次/分，**单次最多 5000 笔**（建议 200–500 笔一批） |

> **注意**：`ts` 必须秒级、且**同一笔交易在不同设备上要能规整成同一个字符串**（都用账单里的同一个时间字段，如 `effectdateStr`）。
> 否则 `(POS, 秒, 金额)` 去重会失效 —— 这是整个方案的关键前提。

### 4.2 服务端分析结果（页面只渲染这个）

```
GET /api/canteen/insights?period=month&limit=10&canteens=桔园,榴园&minTxns=20
```

| 参数 | 默认 | 说明 |
|---|---|---|
| `period` | `month` | `week`(近7天) / `month`(近30天) / `all` |
| `limit` | `50` | 各榜单最多返回条数。**取前十就传 `limit=10`** |
| `canteens` | 无 | 按食堂筛选，逗号分隔。**筛选后占比按子集重算** |
| `minTxns` | `20` | 「之最」评选的最低笔数门槛 |

> **校区筛选由客户端做**：客户端本地已知「校区 → 食堂」，选中校区就把该校区下的食堂列表用 `canteens=` 传上来。

**响应 200（真实输出）**

```json
{
  "period": "近 30 天",
  "startDay": "2026-09-07", "endDay": "2026-10-06", "minTxns": 20,
  "sample": { "terminals": 6, "days": 3, "txns": 405, "totalAmountCents": 551200 },
  "confidence": { "level": "medium", "note": "覆盖 3 天、405 笔交易、6 个窗口，样本中等，结论仅供参考", "txns": 405 },
  "ranking": [
    { "rank": 1, "terminal": "77-140", "name": "麻辣香锅", "canteen": "桔园", "floor": null,
      "txns": 76, "share": 0.1877, "amountCents": 106600, "avgCentsPerTxn": 1402.6 }
  ],
  "canteenRanking": [ { "canteen": "榴园", "txns": 203, "share": 0.501 } ],
  "segments": [
    { "segment": "lunch",  "txns": 237, "share": 0.585 },
    { "segment": "dinner", "txns": 168, "share": 0.415 }
  ],
  "hourly": [ { "hour": 11, "txns": 62, "share": 0.153 }, { "hour": 12, "txns": 105, "share": 0.259 } ],
  "trend": [ { "day": "2026-10-04", "txns": 130 } ],
  "superlatives": {
    "topWindow":      { "terminal": "77-140", "name": "麻辣香锅", "canteen": "桔园", "floor": null, "txns": 76 },
    "topCanteen":     { "canteen": "榴园", "txns": 203, "share": 0.501 },
    "lunchTopWindow": { "terminal": "77-140", "name": "麻辣香锅", "canteen": "桔园", "floor": null, "txns": 45 },
    "dinnerTopWindow":{ "terminal": "77-140", "name": "麻辣香锅", "canteen": "桔园", "floor": null, "txns": 31 },
    "peakDay":        { "day": "2026-10-06", "terminal": "62-119", "name": null, "canteen": "榴园", "floor": null, "txns": 26 },
    "busiestHour":    { "hour": 12, "txns": 105, "share": 0.259 },
    "mostConsistent": { "terminal": "90-200", "name": "黄焖鸡", "canteen": "梅园", "floor": null, "txns": 67, "days": 3, "cv": 0.0512 },
    "leastPopular":   { "terminal": "88-001", "name": "石锅拌饭", "canteen": "榴园", "floor": null, "txns": 61 },
    "mostImproved":   null
  },
  "notes": [
    "无上期数据，「上升最快」暂不可用（需累积至少一个完整周期）",
    "指标为「正餐交易笔数」（数据不含用户标识，无法做同一人的餐次合并）"
  ]
}
```

| 结果块 | 内容 |
|---|---|
| `sample` | 本期样本规模（窗口数 / 天数 / 笔数 / 总金额） |
| **`confidence`** | 置信度：`level`(`low`/`medium`/`high`) + `note`(人话) + `txns`。**客户端可直接做"数据可信度"标签** |
| `ranking` | **窗口榜**：名次、窗口信息、**笔数**、占比、金额、**人均** |
| `canteenRanking` | 食堂榜 |
| `segments` | **餐段分布**（午/晚 及占比） |
| **`hourly`** | **时段分布（几点最挤）** |
| `trend` | 按天趋势 |
| `superlatives` | **之最**：最受欢迎窗口、最热食堂、午/晚冠军、单日峰值、**最挤时段**、最稳定、最冷门、上升最快 |
| `notes` | 人类可读说明（样本不足 / 无上期 / 未命名窗口数…）。**客户端应展示或据此隐藏卡片** |

**客户端必须处理的"暂时没有"**

1. `mostImproved` 为 `null` → 服务端没有上期数据（首月必然），隐藏该卡片或显示"数据积累中"
2. `mostConsistent` / `leastPopular` 为 `null` → 样本天数或笔数不足（`minTxns` 可调）
3. `ranking[].name` 为 `null` → 该终端**还没被命名**，显示「未标注窗口」（建议连 POS 机号一起显示，方便补名字）

### 4.3 精简窗口榜（保留兼容）

```
GET /api/canteen/ranking?period=week&limit=50
→ { "period": "week", "items": [ { "terminal": "...", "name": "...", "txns": 42 } ] }
```
新接入请用 `/insights`（信息更全）。

### 4.4 「必吃榜页面」推荐用法（照抄即可）

```
GET /api/canteen/insights?period=month&limit=10&canteens=桔园,榴园
```

| 页面元素 | 取哪个字段 |
|---|---|
| 窗口名 | `ranking[].name`（`null` 时显示「未标注窗口」） |
| 所属食堂 | `ranking[].canteen` |
| **本周期用餐人次** | `ranking[].txns`（口径为**正餐交易笔数**，展示时可标为"人次"） |
| **人均消费** | `ranking[].avgCentsPerTxn`（单位：**分**，÷100 显示） |
| 名次 | `ranking[].rank`（已按笔数降序，顺序渲染即可） |
| 数据可信度 | `confidence.level` / `confidence.note` |

- **筛选**：校区/食堂/时间跨度 → 对应 `canteens=`（校区传该校区下的食堂列表）与 `period=`
- **建议端侧做 5~10 分钟缓存**，避免用户反复切筛选狂发请求

---

## 5. 管理后台（仅作者本人）

地址 `http://121.37.174.199:8000/admin`（HTTP Basic）。含：映射表检视/编辑、按终端聚合的待审队列、采纳/拒绝、**发布**（点一下才递增版本同步给所有客户端）、**待补终端清单**、**批量粘贴导入**。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/status` | 版本号 / 已发布数 / 草稿数 / 待审数 |
| GET | `/api/admin/reports?status=pending` | 上报队列 |
| POST | `/api/admin/reports/{id}/approve` | 采纳（可带 `name/canteen/floor`），**进草稿** |
| POST | `/api/admin/reports/{id}/reject` | 拒绝 |
| GET | `/api/admin/window-map` | 全表（含草稿态） |
| PUT | `/api/admin/window-map/{terminal}` | 新增/修改（**进草稿**） |
| DELETE | `/api/admin/window-map/{terminal}` | 删除（**进草稿**） |
| POST | `/api/admin/window-map/discard-drafts` | 放弃全部草稿 |
| GET | `/api/admin/unmapped?days=30` | **待补终端清单**（有数据但没名字，按笔数降序）→ 实地踩点用 |
| POST | `/api/admin/window-map/bulk` | **批量补表**（`{"text":"77-139,烤盘饭\n88-001，石锅拌饭"}`） |
| POST | `/api/admin/publish` | **发布**：草稿合并 + `version++` |

---

## 6. 客户端对接清单

- [ ] 「必吃榜」页进入时 `GET /api/canteen/window-map?sinceVersion=<本地版本>`，**离线回退本地缓存**
- [ ] 从账单流水里抽 **食堂类**交易，转成 `{terminal, ts, amountCents, canteen}`
  - `terminal` = `TurnoverRecord.locationName`
  - `ts` = **秒级本地时间字符串**（统一用同一个时间字段，如 `effectdateStr`，规整成 `YYYY-MM-DD HH:MM:SS`）
  - `canteen` = 本地「商户/区域 → 食堂」映射结果
- [ ] 分批 `POST /api/canteen/txns`（建议 200–500 笔一批），**失败可原样重传**（服务端幂等）
- [ ] 进页面时 `GET /api/canteen/insights?period=&limit=10&canteens=`，**直接渲染**
- [ ] 用 `confidence.level` / `note` 展示"数据可信度"标签
- [ ] 账单详情页**多显示一行 POS 机号**（便于作者踩点补窗口名；建议长按可复制）
- [ ] 联调阶段配置明文 HTTP 白名单（见 §1）
- [ ] 写入请求按需带上 `X-Api-Key`（服务端启用后必须带）
- [ ] **不要**在任何请求里带上学号 / 卡号 / 姓名 / 教务会话 / 设备标识

---

## 7. 与需求文档的差异（有意为之）

1. **上传单位从"每日聚合"改为"逐笔交易"**：`{terminal, ts, amountCents}`。这样服务端能自己算餐段、时段分布、趋势等任意维度，**客户端零分析负担**。
2. **完全去标识**：交易表里**连设备/用户哈希都不存**，去重靠 `(POS, 秒)` 唯一约束。
3. **指标是「正餐交易笔数」而非「人次」**：因为没有用户标识，无法做"同一人 5 分钟内合并"。两者≈（差异来自"一顿饭分两次付款"这种少数情况）。
4. **`sinceVersion` 是版本比对而非行级差分**：版本相同返回空 `entries`；不同返回全量，客户端整体替换。
5. **发布机制在服务端内部**：采纳 ≠ 生效，管理员点「发布」才对外。
6. **食堂名由客户端上传自动带**，管理员不必手填（管理员显式填写则覆盖）。

---

## 8. 当前未完成 / 注意事项

| 项 | 状态 |
|---|---|
| 域名 + ICP 备案 | 进行中；到位前用 IP 直连（实测 IP 访问不受备案限制） |
| HTTPS | 域名就绪后服务端改 `SITE_ADDRESS` 一行即自动签发 |
| 个人站前端 | 占位页已上线，等设计稿替换 |
| 管理后台口令 | 部署时随机生成，存服务器 `deploy/.env`（已 `chmod 600`）；**建议改成自己的** |
| 明文期安全 | HTTP 期间管理口令 Base64 明文传输，**别在不可信网络登后台** |
| 「上升最快」 | 需累积一个完整周期后才有值（首月为 `null`） |

---

## 9. 验证记录（服务端自测）

- 本机单元测试 **24 passed**（含去标识断言：交易表里**不存在** token/device 列；同笔重复上传只算一次；同秒不同 POS 不误合并；午/晚口径边界；时段分布；限频与配额）
- 服务器端到端（真实 HTTP，约 400 笔演示交易）：
  `ranking` 6 项 / `canteenRanking` 3 项 / `segments` 午 58.5% 晚 41.5% / **`hourly` 最挤 12 点（25.9%）** / `superlatives` 有值 + `mostImproved: null` 并附 `notes`

---

_文档位置：Qoder 工作区 `docs/必吃榜服务端-接口对接文档.md`｜最后更新 2026-10-06_
