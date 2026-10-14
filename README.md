# 登科喵PDF

无需注册的微信小程序 PDF 工具。蓝白界面、灰白猫形象，提供十项 PDF 转换与处理功能，生成文件下载到手机，临时结果 30 分钟后清理。原资料搜索平台不再是当前产品主流程。

## 功能

PDF 转 Word（文字提取版）、Word 转 PDF（LibreOffice）、合并、拆分、三级压缩、图片转 PDF、PDF 转图片、文字/图片水印、AES-256 加密和输入已知密码解密。首页九宫格和全部工具列表，支持分享工具页面、设备本地收藏。

每日免费成功处理一次，失败返还预占次数。6 元 6 次、12 元 18 次的套餐及看广告获取次数入口已预留，实际支付和广告未开通，不收款、不发放虚假奖励。免费额度按匿名设备凭证而不是微信用户身份计数。

## 本地运行

```powershell
npm ci --prefix frontend
npm run dev:h5 --prefix frontend
# 另一个终端运行后端和数据库
# 先复制 .env.example 为 .env 并填本机配置
docker compose up -d --build
```

后端 Java 21、Spring Boot 3、MySQL 8、PDFBox、Apache POI、LibreOffice；不需要 Redis。Docker 构建会安装 Office 和中文字体。

## 微信开发者工具

```powershell
npm run build:mp-weixin --prefix frontend
```

导入仓库根目录即可，`project.config.json` 指向 `frontend/dist/build/mp-weixin/`。修改前端后重新构建再编译。生产构建读取 `frontend/.env.production`，本地 H5 默认请求 localhost:8080。项目私人配置和构建产物不提交 Git。

## 云托管部署

已有数据库先执行 [004_pdf_tools.sql](database/migrations/004_pdf_tools.sql)，新库可执行 [pdf-tools-init.sql](database/pdf-tools-init.sql)。旧表保留供回滚，不需要删除。生产环境变量参考 [JSON 模板](deploy/pdf-tools.env.json.example)，导入后设 `SQL_INIT_MODE=never`。容器端口 8080，健康检查 `/api/health`。

当前结果保存在实例临时目录，需保持一个活动实例；多实例共享存储和可靠队列尚未接入。服务更新时请先下载已有结果。新版不要求微信 AppSecret 或 COS_BUCKET。

功能边界、价格、数据库、隐私和全部接口见 [PDF_TOOLS.md](docs/PDF_TOOLS.md)。真实支付、广告、分享和手机保存需相应账号配置及真机验收。