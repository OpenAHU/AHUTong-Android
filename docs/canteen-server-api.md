# 安大必吃榜 · 服务端接口对接文档

> 面向：客户端（AHUTong App）开发侧
> 服务端状态：**已开发完成并部署上线**（2026-10-06）
> 对应需求：《安大必吃榜-服务器需求》§2（一期）与 §3（二期）—— **两期均已实现**
> 服务端代码：服务器 `~/projects/ahutong/`（本机开发副本在 Qoder 工作区 `ahutong-server/`）

---

## 0. 一句话

服务端提供 **①「终端码 → 窗口名」公共映射表（读取 + 上报）** 和 **②全校匿名榜单**。
**全链路不含学号 / 卡号 / 姓名等任何个人身份数据**，客户端只上传终端码、商户原文、建议名、消费笔数。

---

## 1. 服务地址

| 环境 | Base URL | 说明 |
|---|---|---|
| **现在（备案前）** | `http://121.37.174.199:8000` | 也可用 `http://121.37.174.199`（80 口同样可用）。**HTTP 明文** |
| 备案 + 域名就绪后 | `https://<你的域名>` | 服务端只需改一行配置即自动启用 HTTPS，接口路径 `base path` 不变 |

> ⚠️ **客户端必须注意**：当前是 **HTTP 明文**，Android 9(API 28)+ 默认禁止明文流量。联调阶段需在 App 里放行该地址：
> - 全局放行：`AndroidManifest.xml` → `<application android:usesCleartextTraffic="true">`
> - 或精确白名单（推荐）：`network_security_config.xml` 里为 `121.37.174.199` 单独开 `cleartextTrafficPermitted="true"`
> 上线换 HTTPS 后应把这些放行**撤掉**。

**连通性自检**（任意机器/浏览器都可试）：
```bash
curl http://121.37.174.199:8000/api/health
# → {"ok":true}
```

交互式接口文档（可直接点着调，方便联调）：`http://121.37.174.199:8000/docs`

---

## 2. 通用约定

| 项 | 约定 |
|---|---|
| 编码 | UTF-8 |
| JSON 字段风格 | **camelCase**（如 `suggestedName`、`sinceVersion`） |
| 请求体 | `Content-Type: application/json` |
| 成功无内容 | 返回 **204**，**无响应体** |
| 错误 | `{"detail": "..."}` + 对应状态码 |
| 鉴权 | **客户端接口无需鉴权**；`/api/admin/*` 与 `/admin` 走 HTTP Basic（仅管理员用，客户端不碰） |
| 时间格式 | 服务端返回 ISO 字符串；上传日期用 `YYYY-MM-DD` |

**错误码**

| 码 | 含义 | 客户端处理 |
|---|---|---|
| 204 | 成功（无内容） | 视为成功 |
| 401 | 管理接口未授权 | 客户端不应出现 |
| 422 | 参数不合法（如 terminal 为空） | 不入库、不重试 |
| **429** | 触发限频（同一 token 每分钟超过上限） | **退避后重试**，不要立刻连发 |
| 503 | 服务端未配置管理口令（仅后台） | 客户端不应出现 |

---

## 3. 一期接口

### 3.1 拉取窗口映射表

```
GET /api/canteen/window-map?sinceVersion={int}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `sinceVersion` | 否 | 客户端本地已缓存的版本号。**与当前版本相同 → 返回空 `entries`（省流量）**；不传 → 返回全量 |

**响应 200**

```json
{
  "version": 7,
  "entries": [
    { "terminal": "77-139", "name": "烤盘饭", "canteen": "桔园", "floor": "一楼" },
    { "terminal": "88-001", "name": "麻辣香锅", "canteen": "榴园", "floor": null }
  ]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `version` | int | 当前映射表版本号，客户端需落盘 |
| `entries[].terminal` | string | 终端码（对应账单 `locationName`，如 `77-139`） |
| `entries[].name` | string | 窗口名 |
| `entries[].canteen` | string | 食堂（学生叫法），可能为空串 `""` |
| `entries[].floor` | string \| null | 楼层，可能为 `null` |

**客户端语义（重要）**

1. **首次**：不带 `sinceVersion` → 拿到全量，落地 `version`。
2. **之后**：带上本地 `version` → 若服务端版本未变，`entries` 为空数组，**直接沿用本地缓存**；若变了，返回的是**全量新表**（不是差分），**整体替换**本地缓存并把 `version` 更新。
3. **离线**：拉取失败时**必须回退本地缓存**（需求 §5.1）。
4. 服务端**不返回**未发布的改动（管理员点「发布」才对外可见），客户端无需感知发布机制。

### 3.2 上报（未标注 / 带名标注）

```
POST /api/canteen/window-report
Header: X-Reporter-Token: <匿名UUID，可选>
Body:
{
  "terminal": "77-139",
  "merchant": "北二区食堂一楼",
  "suggestedName": "烤盘饭",
  "sampleCount": 9
}
→ 204
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `terminal` | ✅ | 终端码，1–64 字符 |
| `merchant` | 否 | 客户端账单里的 `toMerchant` **原文**，帮管理员定位楼层 |
| `suggestedName` | 否 | 用户建议的窗口名，**≤20 字**；**不传/空 = 纯「POS 机未标注」报告** |
| `sampleCount` | 否 | 该用户在此终端的消费笔数（佐证强度），0–100000 |

| 头 | 说明 |
|---|---|
| `X-Reporter-Token` | **可选**。客户端首次启动生成一个随机 UUID 存本地，仅用于**同人去重**与**限频**；用户清除 App 数据即失效。**不含任何身份信息**。 |

**服务端行为**

- **幂等聚合**：同一 `terminal + suggestedName` 合并为一行、累计上报人数，**不产生重复行**。
- **同人去重**：同一 `X-Reporter-Token` 对同一目标重复上报**只计 1 次**。
- **限频**：同一 token **每分钟 ≤5 条**（`429`）。
- **清洗**：服务端会剔除控制字符、去首尾空白、按上限截断。
- **不立即生效**：上报只进**待审队列**，管理员审核 + 点「发布」后才进映射表。

---

## 4. 二期接口（已实现）

### 4.1 上传匿名周期计数

```
POST /api/canteen/stats
Header: X-Reporter-Token: <匿名UUID，可选>
Body:
{
  "entries": [
    { "terminal": "77-139", "meals": 12, "period": "2026-10-06" }
  ]
}
→ 204
```

| 字段 | 说明 |
|---|---|
| `entries[].terminal` | 终端码 |
| `entries[].meals` | 该终端在该周期的**餐次数**（客户端已按需求 §1 口径统计：只算正餐、餐次合并、剔除非食堂） |
| `entries[].period` | **`YYYY-MM-DD`**（按天上传）。为空或格式不对 → 服务端按「今天」处理。单次最多 5000 条 |

**幂等**：同一 `(terminal, day)` 重复上传**取较大值**，不会累加放大 → **客户端可放心重传**。
**限频**：同 token 每分钟 ≤5 次上传请求。

### 4.2 全校榜

```
GET /api/canteen/ranking?period=week&limit=50
```

| 参数 | 默认 | 说明 |
|---|---|---|
| `period` | `week` | `week`(近7天) / `month`(近30天) / `all` |
| `limit` | `50` | 1–200 |

**响应 200**

```json
{
  "period": "week",
  "items": [
    { "terminal": "77-139", "name": "烤盘饭", "canteen": "桔园", "floor": "一楼", "meals": 42 }
  ]
}
```

- 已按 `meals` 降序。
- `name/canteen/floor` 来自映射表；**未命名的终端返回 `null`**（客户端可显示「未标注窗口」或直接过滤）。

---

## 5. 管理后台（仅作者本人；客户端不涉及）

地址 `http://121.37.174.199:8000/admin`（HTTP Basic 鉴权），含：映射表检视/编辑、按终端聚合的待审队列（上报人数多的排前）、采纳 / 改后采纳 / 拒绝、**发布**（点一下才递增版本同步给所有客户端）。

后台 API（`/api/admin/*`，均需 Basic）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/status` | 版本号 / 已发布数 / 草稿数 / 待审数 |
| GET | `/api/admin/reports?status=pending\|approved\|rejected\|all` | 上报队列 |
| POST | `/api/admin/reports/{id}/approve` | 采纳（body 可带 `name/canteen/floor`），**进草稿** |
| POST | `/api/admin/reports/{id}/reject` | 拒绝 |
| GET | `/api/admin/window-map` | 全表（含草稿态） |
| PUT | `/api/admin/window-map/{terminal}` | 新增/修改（**进草稿**） |
| DELETE | `/api/admin/window-map/{terminal}` | 删除（**进草稿**） |
| POST | `/api/admin/window-map/discard-drafts` | 放弃全部草稿 |
| POST | `/api/admin/publish` | **发布**：草稿合并 + `version++` → `{"version":1,"applied":1}` |

---

## 6. 客户端对接清单（对照需求 §4）

- [ ] 新增「必吃榜」页入口，进入时 `GET /api/canteen/window-map?sinceVersion=<本地版本>`
- [ ] 首次全量落盘；后续版本未变则沿用缓存；**离线时用本地缓存兜底**
- [ ] 「求认领」文案改「POS 机未标注」→ 点击 `POST /api/canteen/window-report`（可不带名直接报告）
- [ ] 用户带名标注 → 同时本地保存 + 上报（带 `suggestedName`、`sampleCount`）
- [ ] 生成并本地持久化一个匿名 UUID 作为 `X-Reporter-Token`（**不要**用任何设备指纹/学号）
- [ ] 二期：按需求口径算出每日每终端 `meals` → `POST /api/canteen/stats`（可重传）
- [ ] 二期：渲染 `GET /api/canteen/ranking?period=week`（`name` 为 `null` 即未命名窗口）
- [ ] 联调阶段配置明文 HTTP 白名单（见 §1 提醒）
- [ ] **不要**在任何请求里带上学号 / 卡号 / 姓名 / 教务会话

---

## 7. 服务端与需求文档的差异（客户端需知）

1. **`sinceVersion` 是"版本比对"而非行级差分**：版本相同返回空 `entries`；版本不同返回**全量**。客户端按"整体替换"处理即可。
2. **发布机制在服务端内部**：采纳 ≠ 生效，管理员点「发布」才对外。客户端只看到已发布内容，无需处理草稿。
3. **上报的 `reporterToken` 走 HTTP 头 `X-Reporter-Token`**（需求文档未指定位置，这里定为头，便于限频）。
4. **二期 `period` 语义定为 `YYYY-MM-DD`（按天）**；若你希望改成按周聚合上传，告诉我，服务端可加。

---

## 8. 当前未完成 / 注意事项

| 项 | 状态 |
|---|---|
| 域名 + ICP 备案 | 进行中；到位前用 IP 直连（实测 IP 访问不受备案限制） |
| HTTPS | 域名就绪后，服务端改 `SITE_ADDRESS` 一行即自动签发 |
| 个人站前端 | 占位页已上线，等设计稿替换 |
| 管理后台口令 | 部署时随机生成，存服务器 `deploy/.env`；**建议改成自己的** |
| 明文期安全 | HTTP 期间管理口令是 Base64 明文，**别在不可信网络登后台** |

---

## 9. 验证记录（服务端自测）

- 本机单元测试 **12 passed**
- 服务器端到端（真实 HTTP）：上报 `204` → 待审队列出现 → 采纳 `204` → 发布 `{"version":1,"applied":1}` → 客户端 `window-map` 拿到新名 → 同版本增量返回空 → 二期上传 `204` + 榜单返回 `meals:42`
- 覆盖需求 §5 全部 5 条验收标准

---

_文档位置：Qoder 工作区 `docs/必吃榜服务端-接口对接文档.md`｜最后更新 2026-10-06_
