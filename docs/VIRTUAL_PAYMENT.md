# 微信虚拟支付接入与配置

当前通道：道具直购 `wx.requestVirtualPayment`（`short_series_goods`）。生产真机使用 `env=0`，不是商户支付 `wx.requestPayment`。月卡和加油包均为主动单次购买，无自动续费。

## 平台商品（用户已发布）

| 业务套餐 | 微信道具ID | 价格（分） | 权益 |
| --- | --- | --- | --- |
| monthly | ai_monthly | 990 | 一个会员周期200万加权额度 |
| ai-topup | ai_topup | 990 | 100万加权额度，长期有效、可叠加 |

真实环境以微信后台发布的商品资料及权限为准。虚拟支付商品均按服务器固定990分下单，不接受客户端价格、数量或额度。普通商户支付仍保留兼容代码，只有显式配置 `PDF_PAYMENT_PROVIDER=wechat` 才会用于新订单；旧通知入口不会为虚拟订单发额度。

## 数据库升级

已有安装先确认008、009、010已执行，再运行 `database/migrations/011_virtual_payment.sql`。011只创建新表，可以重复执行，不修改旧订单或余额。`CREATE TABLE IF NOT EXISTS` 不会给旧表补字段，不能用重跑初始化SQL替代历史升级脚本。

新表存储订单商品/用户/环境快照、退款累计额和补偿查询时间，不保存AppKey或session_key。`SQL_INIT_MODE=never` 的云端必须先手工执行升级。

## 当前推荐：云托管消息推送，不配置自定义域名

环境变量设置 `WECHAT_VIRTUAL_NOTIFY_MODE=cloudrun`（默认值），保留OfferID、AppKey、AppID、AppSecret及两个商品ID。此模式不需要Token/EncodingAESKey，已有值可保留但不用于内网转发。不要在公众平台的公网URL表单填写默认测试域名。

在微信云托管“设置 → 全局设置 → 消息推送 → 添加消息推送”中配置。选择环境prod、服务document-assistant、格式JSON，服务内路径填：

`/api/pdf/virtual/cloudrun-notify`

应先部署代码后保存配置，平台用CheckContainerPath探测，接口返回success。云托管转发消息通过平台来源头x-wx-source（兼容x-wx-sources）检查；如有x-wx-appid也校验匹配。发货/退款仍要向微信查单核实真实订单和金额，来源头不能直接授予额度。须从微信云托管网关进入服务，不要把此信任配置用于直接暴露容器端口的其他托管平台。

## 云托管后端环境变量

以下已有配置继续使用：

```
WECHAT_APP_ID=<小程序AppID>
WECHAT_APP_SECRET=<小程序AppSecret>
WECHAT_VIRTUAL_OFFER_ID=<OfferID>
WECHAT_VIRTUAL_APP_KEY=<现网AppKey>
WECHAT_VIRTUAL_SANDBOX_APP_KEY=<沙箱AppKey>
WECHAT_VIRTUAL_MONTHLY_PRODUCT_ID=ai_monthly
WECHAT_VIRTUAL_TOPUP_PRODUCT_ID=ai_topup
```

新增/确认（Token/AES只用于备用http模式）：

```
WECHAT_VIRTUAL_NOTIFY_MODE=cloudrun
PDF_PAYMENT_PROVIDER=virtual
WECHAT_VIRTUAL_ENV=0
WECHAT_VIRTUAL_NOTIFY_TOKEN=<自定的3至32位英文字母或数字>
WECHAT_VIRTUAL_ENCODING_AES_KEY=<微信后台随机生成的43位EncodingAESKey>
PDF_BILLING_ENABLED=false
PDF_AI_TEST_MODE=false
```

Token、EncodingAESKey在微信消息推送和云托管中必须完全一致。EncodingAESKey与AppKey是不同的密钥。密钥只放后端环境，不得放进代码仓库或小程序包。修改云托管环境变量后重新部署/重启生效。

沙箱默认禁止购买；如确需沙箱调试，需要隔离数据库/测试用户，并显式设置 `WECHAT_VIRTUAL_ENV=1`、`WECHAT_VIRTUAL_ALLOW_SANDBOX=true`。不得将沙箱权益混入现网数据库。Android/iOS的实际支付资格、客户端版本、沙箱可用版本依平台规则；真实真机联调使用现网AppKey和env=0。iOS还需在微信平台开通对应IAP能力，旧客户端不支持时前端提示更新，不回退到普通商户支付。

## 公网AES推送（备用，仅自有域名场景）

这条备用方案要求 `WECHAT_VIRTUAL_NOTIFY_MODE=http`，测试域名不能在微信正式URL表单提交。当前项目优先使用下面的云托管方案。

微信平台虚拟支付页的“发货推送配置 / 前往配置”或“开发管理 / 消息推送”，按实际界面进入：

- URL：`https://document-assistant-328135-12-1505937649.sh.run.tcloudbase.com/api/pdf/virtual/notify`
- Token：与 `WECHAT_VIRTUAL_NOTIFY_TOKEN` 相同。
- EncodingAESKey：与 `WECHAT_VIRTUAL_ENCODING_AES_KEY` 相同。
- 消息加解密模式：**安全模式（AES加密）**。
- 数据格式：JSON（也支持XML）。
- 订阅事件：道具发货 `xpay_goods_deliver_notify`、退款 `xpay_refund_notify`。

GET验证地址支持签名验证和明文/加密echostr；POST只接受AES安全消息，验证签名、有效时间、AppID，禁用XML外部实体。不要把普通微信支付 `/api/pdf/pay/notify` 填到这里。

发货推送配置保存成功后，设置 `PDF_BILLING_ENABLED=true` 并重启后端。`/api/pdf/config` 应返回 `paymentProvider=virtual`、`billingEnabled=true`、`paymentEnabled=true`；这只证明本地必填配置齐备，不代表微信授权、AppKey签名、商品权限和实际支付已经验证。

微信服务器获取access_token可能受平台IP白名单限制；若后台启用了白名单，按错误码和云服务出口IP配置，不要把手机IP填成后端出口IP。

## 服务端确认和退款

- 支付会在服务端重新用wx.login的code换取session_key并签名，不向客户端返回session_key或AppKey。
- 支付通知使用安全模式验真后仍通过 `/xpay/query_order` 验证订单号、金额、环境和平台单号；前端success不能发额度。
- 金额固定990分、数量固定1，商品ID来源后端；通知里的商品/用户/环境须匹配订单快照。
- 在同一个数据库事务中锁定账户与订单，发权益并更新paid，重复/并发通知不会重复发货。平台交易单号有唯一约束。
- 前端按服务器订单状态确认成功，网络异常保留本地订单引用供查询；不存支付密钥。取消付款不显示购买成功。
- 前台查询和每分钟补偿任务均会检查未完成订单；同单节流，按批次限量。未支付旧单降低查询频率。支付结果确认后调用notify_provide_goods，失败后重试；已支付订单每天查一次用于漏收退款恢复，最长追溯365天。
- 退款以平台查单的累计金额为准，按增量回收。加油包已消耗部分可能产生负余额，后续额度抵扣。月卡只收回尚未消耗的时间，不刷新已使用的月额度；其他有效续费顺延调整。重复退款不重复回收。
- 无支付权限、签名/金额/环境不符和未知状态均不发额度，记录有限错误分类，不打印密钥或回调原文。
- 未实现供用户主动发起退款的接口；iOS按平台渠道退款。处理xpay_subscribe_ios_refund_query_notify时同意平台退款流程，实际权益以退款完成查单结果回收。

## 验证边界

自动测试覆盖：两种AppKey签名、session签名、access_token过期重试、安全回调JSON/XML验真、错误AppID/签名/时间/XXE、通知去重、查单补发、并发发货、部分退款、已消耗额度退款、月卡续费退款及前端取消/失败/能力缺失行为。

合成样本验证不代表真机付款、iOS IAP、发货推送及结算已完成。部署配置后使用微信真机做一次真实购买，核对平台订单、后端paid状态、额度到账和后续AI实际扣减。

## 参考

- 微信客户端API：https://developers.weixin.qq.com/miniprogram/dev/api/payment/wx.requestVirtualPayment.html
- 微信查单：https://developers.weixin.qq.com/miniprogram/dev/server/API/VirtualPayment/api_query_order
- 微信消息推送：https://developers.weixin.qq.com/miniprogram/dev/framework/server-ability/message-push.html
- 腾讯CloudBase接入参考：https://github.com/TencentCloudBase/skills/blob/main/skills/cloudbase-wechat-integration/references/virtual-payment.md
