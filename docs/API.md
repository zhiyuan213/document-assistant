# 第一阶段接口

基础路径 `/api`。除健康、登录与商品外，接口要求 `Authorization: Bearer <token>`。失败返回 `{ "message": "说明" }`；未登录401、无权限403、无效文件400、超限429、忙碌或未配置503。

| 方法 | 路径 | 内容 |
| --- | --- | --- |
| GET | `/health` | 服务状态和开发登录是否启用 |
| POST | `/auth/login` | JSON `{code,development:false}`；H5本地使用`{development:true}` |
| GET | `/me` | 用户信息和每日次数 / 高级权益 |
| POST | `/process/images` | multipart重复`files`字段，`landscape`、`highQuality`布尔字段 |
| POST | `/process/compress` | multipart单个PDF |
| POST | `/process/merge` | multipart 2–10个PDF，按传入顺序 |
| POST | `/process/ocr` | multipart单个JPG/PNG，最多5MB |
| GET | `/files?kind=all&page=1` | kind为all/pdf/txt，每页20条，返回items/total/page |
| GET | `/files/{id}/download` | 返回文件字节及Content-Disposition |
| DELETE | `/files/{id}` | 删除自己的结果文件，返回deleted=true |
| GET | `/product` | 商品和定价（分），当前enabled=false |
| POST | `/orders` | 尚未实现支付，返回503，不收款 |

处理成功返回 `{file,taskId,savedBytes,text?}`。`file`包含id/name/kind/size_bytes/source_bytes/created_at等元信息。OCR同时保存一份原始识别TXT；客户端编辑不自动覆盖历史文件。接口输入文件不持久保存，结果文件保存在本地数据卷或COS。

免费额度在处理前以数据库条件更新原子预占，同一天多个并发请求不会超过上限；失败时退回预占日的额度。全局处理并发限制为单实例2项。第二阶段应补充微信支付回调验签、金额核对、幂等订单更新、权益授予及退款流程；当前接口不允许客户端自行增加权益。
