# 登科喵资料助手

面向考试、求职、学习、办公场景的资料检索与文件处理小程序。采用 uni-app + Vue 3、Spring Boot 3、MySQL 8、Redis、PDFBox。根据用户最新确认，界面调整为紫白主题，新增资料搜索、分类、收藏和下载，保留原文件工具。资料平台说明见 [RESOURCE_LIBRARY.md](docs/RESOURCE_LIBRARY.md)。

线上部署目标为**微信云托管 + MySQL 8.0**，配套Redis、COS和腾讯云OCR。详细架构、环境变量与发布顺序见 [微信云托管部署说明](docs/DEPLOY_WECHAT_CLOUD.md)。小程序AppID已配置，实际云资源尚未创建。

## 已实现

- 微信 code 登录、7天 Redis 会话；H5 本地开发登录（生产默认关闭）。
- 1–20张 JPG/PNG 按顺序转 A4 PDF，支持竖版/横版、标准/高级高清质量。
- 2–10份 PDF 合并，保留页序、文字与资源，总页数最多200页。
- PDF 内图片智能压缩；保留文字与页面结构，结果不大于原文件。已压缩文件可能不再缩小。
- 腾讯云通用文字识别，结果复制与 TXT 导出；OCR图片最多5MB。
- 文件历史、PDF/文字筛选、分页、下载/打开、删除，服务端校验所属用户。
- 资料库分类、中文关键词搜索、本人收藏、流式下载与下载管理；实际资料计数，开发环境提供原创示例。
- 每日5次免费处理（北京时间重置，可配置），失败退还次数；高清导出校验高级权益。
- 最新设计的首页、分类、搜索、资料详情、收藏、下载管理和个人中心，以及原工具中心、PDF/OCR处理、我的文件和高级工具包。页面截图见 [UI预览](docs/UI_PREVIEW.md)。
- 本地文件存储 / 腾讯 COS 私有桶两种模式，文件通过认证接口下载。

## 当前边界

这是第一阶段可运行开发版本，尚未完成正式发布。微信登录、腾讯云OCR与COS需要配置自己的凭据，仓库不包含密钥，也不模拟识别结果。第二阶段微信支付尚未接入，高级工具包页显示 ¥9.9 与“即将开放”，支付接口明确返回未开放；不能购买或自动授予权益。当前高级权益的数据结构与校验已就绪，商品说明待正式支付版本确认。

文件任务当前同步执行，每个后端实例同时最多处理两项任务；繁忙请求返回重试提示。大型文件、复杂PDF、手机内存限制与微信真机上传需要继续验证。PDF压缩可能降低图像质量，暂不处理Form内嵌图片。结果文件一直保留至用户主动删除，未实现定期过期清理；原始输入仅在本次处理时使用，OCR记录随对应TXT删除。

页面猫咪使用用户提供的「登科喵设计图.zip」中的资料助手橘白猫及本次透明背景版本，素材映射见 `reference/mascot-design/README.md`。PDF拆分、加密、证件专用识别等工具尚未开放。

## 启动

需要 Docker Desktop（Linux容器）及 Node.js 20+；本地直接运行后端需要 Java 21 / Maven 3.9+。

```powershell
# 在项目根目录，首次创建配置
Copy-Item .env.example .env
docker compose up -d --build

# 前端，推荐pnpm 11，依赖版本由锁文件固定
cd frontend
pnpm install
pnpm dev:h5 --host 127.0.0.1 --port 5173
```

打开 `http://127.0.0.1:5173`。接口默认 `http://localhost:8080`。`compose.yml` 只绑定本地端口，默认启用开发体验登录，共用一个明确的开发用户；只适合本机调试。自行使用浏览器其他域名时，将 `.env` 的 `CORS_ORIGIN` 改为对应源。

本机提供了 `scripts/start-frontend.ps1` 和 `scripts/start-backend.ps1`，可以使用已下载到被Git忽略的 `.tools/` 中的Java运行时。前端脚本直接使用本地编译器，不依赖本机npm是否正常。

```powershell
# 也可以只在Docker启动数据库，直接运行已构建的后端
docker compose up -d mysql redis
./scripts/start-backend.ps1
# 另开终端
./scripts/start-frontend.ps1
```

SQL源为 `backend/src/main/resources/schema.sql`，独立副本为 `database/init.sql`。应用启动时创建不存在的表；不执行破坏性重建。MySQL数据库本身由Compose创建。核心用户表使用 `app_user` 命名。

如果Windows本地JDK出现loopback/UnixDomainSockets启动错误，建议运行Linux容器。本地已构建JAR后可使用 `docker compose -f compose.yml -f compose.local.yml up -d --build`，避免在容器中重复下载Maven依赖。

## 微信小程序

1. 填写 `frontend/src/manifest.json` 的 `mp-weixin.appid`，配置后端 `WX_APP_ID` / `WX_SECRET`。
2. 前端 `.env` 配置 `VITE_API_URL=https://你的接口域名`。后端配置 MySQL、Redis 和可选云凭据。
3. 执行 `pnpm build:mp-weixin`，微信开发者工具导入 `frontend/dist/build/mp-weixin`。
4. 在微信后台设置 request / downloadFile 合法域名。真机不能使用电脑的localhost。
5. 上线关闭 `DEV_LOGIN`，使用生产数据库密码，配置HTTPS和完整隐私政策，并验证登录、上传、OCR与文件打开流程。

微信端图片来自相册或相机，PDF通过聊天文件选择。多文件以multipart按当前顺序上传；总文件大小前端限制90MB，后端单文件20MB/请求100MB。小程序PDF结果通过 `openDocument` 打开并可从菜单分享/保存；文字结果可复制，H5支持直接下载TXT。OCR编辑只影响当前复制内容，已保存TXT为原始识别结果。

## 云配置

根目录 `.env.example` 列出所需配置；不要提交 `.env`。OCR使用腾讯云 `GeneralBasicOCR`。COS设置 `STORAGE=cos`、`COS_BUCKET`、`CLOUD_REGION` 和腾讯云密钥，桶保持私有。未设置OCR密钥时返回明确错误并退还次数。OCR与COS会产生云服务调用费用。

## 验证

```powershell
cd backend
mvn test
mvn package
cd ../frontend
pnpm build:h5
pnpm build:mp-weixin
```

测试采用H2的MySQL兼容模式和模拟Redis会话，覆盖PDF页序与字体资源、压缩后文字、用户隔离、失败退回次数、额度耗尽、高清权益和登录会话过期。云服务及微信真机需要凭据后另行验收。接口说明见 `docs/API.md`。

技术参考：[PDFBox 3迁移指南](https://pdfbox.apache.org/3.0/migration.html)、[uni-app Vue 3说明](https://uniapp.dcloud.io/tutorial/vue3-basics)。前端依赖模板来自DCloud官方 `uni-preset-vue` 的vite分支。
