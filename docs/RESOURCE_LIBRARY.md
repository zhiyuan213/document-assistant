# 资料检索与文件工具

本次根据用户最新资料助手设计图与确认，将产品扩展为资料检索/分类/收藏/下载平台，并保留原PDF与OCR文件工具。最新视觉使用紫白主题，覆盖首页、分类、搜索、详情、收藏、下载管理及个人中心；文件工具也统一采用紫色主色。设计参考保存在 `reference/ui-library-design.png`。

## 可用功能

- 首页关键词搜索、八类入口、真实热门资料列表、文件处理工具入口。
- 分类侧栏与六种资料类型筛选，支持中文名称、简介和标签搜索。
- 资料详情展示大小、类型、标签、描述和相关资料；可收藏与下载。
- 收藏写入MySQL，添加幂等、取消收藏仅作用于本人。
- 下载记录写入MySQL，支持进度、暂停、重新下载、完成与失败状态、分页。点击继续会从头下载，未实现HTTP Range断点续传。
- H5下载到浏览器下载目录；小程序PDF/Office文档通过原生预览打开，TXT内容可复制。微信真机、Office格式和大型文件需要进一步验收；不保证原生预览支持ZIP。
- 个人中心显示本人收藏数、下载数和文件数，保留PDF/OCR工具与我的文件；意见反馈保存到本服务数据库。

## 资料来源与开发样例

`SEED_DEMO=true`且`DEV_LOGIN=true`时，后端首次启动会生成八份**原创示例**TXT/PDF文件，标题、简介明确标注“示例”。不包含未经提供的真题，不伪造下载量。现有本地Compose默认开启示例；生产设置`SEED_DEMO=false`和`DEV_LOGIN=false`。资料内容上传/发布管理后台尚未实现，正式资料需由维护者通过数据库与存储维护导入。

示例文件存储在与处理结果相同的Storage适配层，支持本地开发或COS。生产资料使用私有COS桶，数据库只存储存储键和公开元信息；下载接口验证用户与权益，再返回文件字节，不暴露存储键或长期公开下载URL。

文件处理的每日免费额度沿用原逻辑；当前免费资料下载不扣文件处理次数。会员资料由服务端权益校验，支付接口仍未开放。

资料下载后端以64KB缓冲流式返回，避免整份大文件读入后端堆内存。当前下载请求超时120秒；大型资源仍需在实际云环境和微信真机上验证。

## 新接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/categories` | 分类和真实资料数量 |
| GET | `/api/resources?q=&category=&topic=&sort=popular&page=1` | 公共搜索，sort支持popular/recent，每页20条 |
| GET | `/api/resources/{id}` | 资料详情、本人收藏状态及相关资料 |
| GET | `/api/favorites` | 本人收藏，可按关键词/分类筛选 |
| POST / DELETE | `/api/resources/{id}/favorite` | 收藏 / 取消收藏 |
| POST | `/api/downloads` | JSON `{resourceId}` 创建本人下载任务 |
| GET | `/api/downloads?page=1` | 本人下载历史 |
| GET | `/api/downloads/{id}/file` | 认证后下载文件，第一次实际返回文件时计数 |
| PUT | `/api/downloads/{id}` | JSON `{status}`；started/paused/completed/failed |
| GET | `/api/library/summary` | 本人收藏、完成下载和处理文件数量 |
| POST | `/api/feedback` | JSON `{content}` 保存本人5–1000字建议 |

收藏和下载必须登录，公共搜索/分类无需登录。已下架资料不可下载；完成状态必须先经过实际文件服务，同一个任务重复读取只计一次下载。下载管理按用户隔离，不跨账号复用缓存；退出登录会清理当前客户端的下载缓存与活动任务。

使用PUT更新状态是为兼容微信小程序支持的请求方法。[uni.request官方方法兼容表](https://uniapp.dcloud.net.cn/api/request/request)

## 数据库升级

新表：resource_category、resource_item、resource_favorite、resource_download、user_feedback。

本机开发应用在启动时创建缺失表，保留已有用户、文件与权益。云端使用`SQL_INIT_MODE=never`时，在发布新镜像前执行 `database/migrations/002_resource_library.sql`。不要重建或删除已有业务表。

## 品牌图片

内置imagegen以用户提供的资料助手原图为编辑输入，仅提取透明背景的猫咪与资料盒，保存到 `branding/dengke-doc-cutout.png` 和 `frontend/src/static/brand/dengke-doc-cutout.png`。页面通过brand.js统一引用。提示词要点：保留橘白猫、星光眼睛、红围巾、登字徽章、耳饰、资料盒及标签，移除外侧白底、紫色圆形背景与悬浮装饰，输出真实透明PNG。

## 验证

后端集成测试覆盖搜索转义与组合筛选、收藏幂等和隔离、会员/下架限制、下载计数、完成状态与暂停重新开始，以及历史分页。原登录、PDF和权益测试保留。H5与微信小程序两种构建均需通过；浏览器还需验证搜索→详情→收藏→实际文件下载→历史记录，以及原文件工具可进入。
