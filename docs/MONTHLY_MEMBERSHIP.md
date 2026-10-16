# AI 月卡与 Token 额度

基础 PDF 转换、压缩、合并、拆分和其他工具永久免费、不限次数。收费项仅为 DeepSeek AI 文档功能。AI 月卡建议价 **9.90 元 / 月**，不自动续费；每天最多请求 50 次，另受每月 Token 额度控制，所以小问题可以多问，长 PDF 会扣更多额度。

用户选择 PDF 后先点击“估算 Token 用量”。服务端读取页面文本并估算输入 token，不调用大模型；视觉识别按每张图片最多 1,024 tokens 预留。确认后再发送到 DeepSeek。估算使用官方公布的字符/token近似比例并乘安全系数，处理前展示预留量；响应完成后用 API 返回的真实输入、输出 token 结算，退回多余预留。模型请求已发出但超时且没有实际计量回执时保留预留额度，防止已产生费用却退回全部额度。

为反映输入和输出单价差异，会员月额度以“输入 Token 等价值”计：`实际输入 tokens + 4 × 实际输出 tokens`。图片输入 token 由 DeepSeek 返回的 prompt 用量一并结算。默认每个会员周期额度 **2,000,000 等价值 tokens**；服务端上限不能被环境变量调高。每次最多 30 页、15,000 个提取文字字符，扫描件 OCR 最多 5 页，输出最多 1,500 tokens；预估额度超过月额度与加油包余额合计时不能提交，并提示购买加油包。

## AI 加油包

首版仅一档：**9.90 元 / 200 万加权 Token 额度**。可独立购买、叠加购买，额度长期有效，会员到期后仍可用。扣费先用当前会员周期月额度，再用加油包；月额度不结转，续费不会立即刷新当前周期额度，到下一周期才刷新。每日最多 50 次是账号防滥用限制，加油包不增加次数上限。

每次请求先在事务中锁定同一微信账号的余额，预占两种额度；根据实际用量退回差额到原来源。跨会员周期返回的结果不恢复已过期月额度。若估算低于实际计量，差额计入加油包余额（可能暂为负，后续额度先抵扣），避免静默免单。未发起模型请求的失败退回全部预占；已发送但无用量回执保留预占。

支付订单固定 `package_id=ai-topup`、990 分、2,000,000 额度，由服务端确定；客户端只传套餐 ID。已验签且金额、商户匹配的支付成功通知在同一事务内发放余额并标记订单已支付，重复通知不重复发放。不自动扣款。

## 成本预算

截至 2026-10-16，按 DeepSeek 国内人民币高峰价格统一预算：Flash 缓存未命中输入每百万 tokens **2 元**，输出每百万 tokens **8 元**，不计缓存与空闲时段优惠。输入和输出按 1:4 折算，200 万加权额度全部用满的模型成本为 **4 元**。月卡与加油包均售 9.90 元，扣模型费后剩 5.90 元；支付手续费和按量基础设施成本合计不超过 **0.95 元/份**时，贡献利润率可达到 50%。固定成本、税费、获客和客服另算，不能保证公司净利润 50%。

DeepSeek 官方 Token 用量说明给出的粗略换算是中文字符约 0.6 token、英文字符约 0.3 token；实际以 API 回执为准。举例：15,000 个中文字符约 9,000 输入 tokens，加上 1,500 输出 tokens，约扣 15,000 等价值额度。月额度大约容纳 133 次这样的最大请求；如果每天 50 次都处理大文件，约三至四天会先用完月额度。小文件和简短问题消耗少得多。参考：[官方模型价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)、[Token 估算说明](https://api-docs.deepseek.com/quick_start/token_usage/)、[视觉图片 Token 上限](https://api-docs.deepseek.com/guides/vision/)。

价格会变。上线后每周将 `pdf_ai_usage.input_tokens`、`output_tokens` 与 DeepSeek 账单核对；如 30 天真实模型及其他按量成本超过 4.95 元/会员，应下调月 Token 额度或调高售价。预算统一按国内较高单价核算，优惠差额作为额外余量，不使用美元汇率。

查看每月成功响应的实际用量：

```sql
SELECT usage_month, SUM(request_count) AS requests,
       SUM(input_tokens) AS input_tokens, SUM(output_tokens) AS output_tokens,
       SUM(used_credits) AS token_credits
FROM pdf_ai_usage
GROUP BY usage_month
ORDER BY usage_month DESC;
```

DeepSeek 控制台账单是最终费用凭据；数据库汇总用于对照各月会员消耗，不含 API 未返回 usage 的失败请求成本。

## 部署与支付

旧库先执行 `database/migrations/007_monthly_membership.sql`，随后按需执行 `database/migrations/008_wechat_pay.sql` 、`database/migrations/009_pdf_ai.sql` 和 `database/migrations/010_pdf_ai_topup.sql`。新数据库直接使用更新后的 `database/pdf-tools-init.sql`。迁移 010 创建 `pdf_ai_wallet`，持久保存会员周期与加油包余额；月度用量表保留为账单汇总，不再作为扣费余额。迁移 009 创建 AI 月度 Token 额度表和每日请求计数表；如果试验库已用旧结构创建 `pdf_ai_usage`，需增加 `used_credits`、`input_tokens`、`output_tokens` BIGINT 字段。

`PDF_BILLING_ENABLED=true` 仅用于开放 AI 月卡及加油包购买，不限制基础 PDF 免费工具；未配置微信支付时购买入口保持关闭。`PDF_MONTHLY_PRICE_FEN` 默认 990（9.90 元），服务端最低价保护为 990。`PDF_AI_MONTHLY_TOKEN_CREDITS` 默认 2,000,000，`PDF_AI_DAILY_REQUEST_LIMIT` 默认 50；配置值不能超过代码中的上限。会员额度按已绑定微信 OpenID 合并，换设备或重领匿名设备凭证不会重置月 Token 额度。DeepSeek 密钥只放服务器环境变量：`DEEPSEEK_API_URL`、`DEEPSEEK_API_KEY`、`DEEPSEEK_MODEL`。

支付走微信支付 API v3 JSAPI。环境变量需配置 `WECHAT_APP_ID`、`WECHAT_APP_SECRET`、`WECHAT_PAY_MCH_ID`、`WECHAT_PAY_MCH_SERIAL_NO`、`WECHAT_PAY_MCH_PRIVATE_KEY_BASE64`、`WECHAT_PAY_API_V3_KEY`、`WECHAT_PAY_PLATFORM_SERIAL`、`WECHAT_PAY_PLATFORM_CERT_BASE64` 和 `WECHAT_PAY_NOTIFY_URL`。私钥与平台证书为 PEM 内容的 Base64 编码；APIv3 密钥为商户平台的 32 字节密钥。真实支付需在微信开发者工具及真机验收，构建成功不代表支付链路已验证。

## 测试免扣额度开关

在后端环境变量设置 `PDF_AI_TEST_MODE=true` 并重新部署/重启，可跳过 AI 会员及余额拦截。无需购买月卡或加油包，估算预占为 0，实际处理不扣月额度或加油包；记录真实输入/输出 Token 用于核对服务商费用。每日请求上限和文件、页数、输入、输出限制仍有效。该开关由后端控制，前端参数不能开启；默认 `false`。页面显示测试模式，测试结束后改回 `false` 并重启，即恢复正常付费校验。

这不是 DeepSeek 免费接口：仍需配置密钥，真实 API 费用仍从 DeepSeek 账户扣除。`PDF_BILLING_ENABLED=false` 仅关闭购买入口，不会解除 AI 付费额度拦截，测试请使用独立的 `PDF_AI_TEST_MODE`。该开关为整个后端服务生效。
