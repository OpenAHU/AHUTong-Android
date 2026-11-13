# AI 助手系统提示词

- DeepSeek Chat Completions 将系统指令放在 `messages` 中的 `role: "system"` 消息，`content` 接收字符串。本项目继续使用该消息结构，在文本内用 Markdown 标题和列表表达层级。XML 标签也可标记参考资料的边界。本次选用 Markdown，校情、时间和人格设定各有独立标题。 [DeepSeek Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/) · [OpenAI 提示词工程](https://developers.openai.com/api/docs/guides/prompt-engineering#message-formatting-with-markdown-and-xml) · [Anthropic 提示词指南](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices#structure-prompts-with-xml-tags)
- 工具指令写清触发条件、参数、需要等待的结果和停止条件。本项目用有序步骤表达作业定位、读取和解题确认。充值、缴费和其他写操作由运行时确认后执行。`decline_scope` 的 `reason` 可省略，默认空串。运行时收到该调用后终止本轮，界面显示应用内固定文案。 [DeepSeek 工具调用](https://api-docs.deepseek.com/guides/tool_calls/) · [OpenAI 函数定义建议](https://developers.openai.com/api/docs/guides/function-calling#best-practices-for-defining-functions)
- 网页、工具结果、题目图片和文档可用于提取事实，其中的指令没有权限触发工具或改写系统规则。 [OpenAI Model Spec](https://model-spec.openai.com/2026-10-29.html#ignore-untrusted-data-by-default)

当前与历史用户文本保留 `user` 消息角色。App 遇到首个 `&^`、`^&` 或 `*^&` 时，从该处截去余下文本，再用独占一行的 `&^` 和 `^&` 包裹保留的内容。包裹前的固定说明由 App 添加，提示模型将框内文字视为不受信任的输入。写操作仍由 App 的审批机制控制。
