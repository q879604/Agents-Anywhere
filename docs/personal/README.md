# 个人自用版维护说明

这是 [`q879604/Agents-Anywhere`](https://github.com/q879604/Agents-Anywhere) 的个人自用分支，基于
[`anywhere-labs/Agents-Anywhere`](https://github.com/anywhere-labs/Agents-Anywhere) 的 v2 主线。
它保留了官方仓库的目录、协议和部署方式，同时把平板端、Android 附件/图片体验以及自托管服务端的
若干改动固定下来，方便以后重新构建、部署或在另一台机器上复用。

> 个人自用版 APK 使用独立签名证书，**不能覆盖安装官方签名的 APK**。从官方版切换时需要先备份数据并卸载官方版；
> 之后再安装本仓库构建的 APK。证书和密码只保存在本机的私密位置与 GitHub Actions Secrets 中，不进入仓库。

## 当前快照

| 组件 | 版本/位置 | 说明 |
| --- | --- | --- |
| Android | `2.1.13`，`versionCode 23` | Release tag `v2.1.1-tablet.27`；包含平板分栏、附件菜单、工作区/对话图片预览、应用级待发队列 |
| Server | 分支 `feat/apk/tablet-adaptive-layout`（`883f309`） | 附件流式上传/下载、缩略图接口，见下文 |
| DSH Bridge | `dsh-bridge-next/` | `e1d2d43` 增加 preset 隔离时的原生 compact fallback |
| 个人快照标签 | `personal-v2.1.13` | 本说明和当前服务端快照提交所在标签 |

Android 当前 APK：

- 文件：`AgentsAnywhere-2.1.13-release.apk`
- SHA-256：`293a2477cb4876951e9772ba839598d562a39a20f5cb8813b58a043fe7e06833`
- 签名证书 SHA-256：`180e8f4e41fe74d12960a3d08f2fee29b3e813cf339c252fe88777fcf05e5489`
- GitHub Release：<https://github.com/q879604/Agents-Anywhere/releases/tag/v2.1.1-tablet.27>

## 与官方版的主要差异

### Android

- 平板（`smallestScreenWidthDp >= 600` 且宽度 `>= 720dp`）使用左侧栏 + 会话详情分栏；手机保持单栏。
- 侧栏可收起，空会话页与聊天页使用同一个顶栏按钮。
- 输入框加号菜单改为紧凑四列网格：照片、拍照、视频、音频、文件、剪贴板、压缩图片；运行时如果确实提供
  `compact` 命令，再显示「压缩上下文」。
- 图片：上传前按档位压缩；工作区图片可从终端文件页直接打开；工具/命令输出中的工作区截图会直接出现在聊天消息里。
- 待发消息：会话运行中仍可输入；消息进入应用级待发队列，切换对话或普通退到后台后会继续等待并在回合结束后发送。
  该队列是 App 进程内队列，手动强制停止进程会丢失。
- 流式时间线、深色键盘、返回过渡、思考链展开闪退/闪动等修复已包含。

### Server

当前自用快照在官方 v2 服务端之上增加了：

- 上传改为 socket → 磁盘流式写入，上传数量上限从 5 提高到 10；
- 本地文件下载直接流式返回，不再先读入内存；支持 `ETag` / `If-None-Match` 缓存校验；
- 新增图片缩略图：`GET /api/v2/sessions/{session_id}/attachments/{file_id}/thumb?w=512`
  （宽度会归一到 256/512/1024，首次访问时生成 WebP，失败时返回 404，不影响原图）；
- 新增 `pillow>=11` 依赖，用于缩略图解码/编码；缩略图文件与原文件放在同一存储目录，不增加数据库迁移。

### DSH Bridge

`e1d2d43` 在 DSH preset 把 `command-compact` 注册在隔离子树、host registry 看不到它时，
从 `agentPresets.serviceFor(agent, 'compaction')` / `agent.ctx` / host `ctx` 回退到原生
`compactNow(...)`；不会把 `/compact` 当普通聊天文本发送。

## 重新构建 Android

构建入口是 [`.github/workflows/android-apk.yml`](../../.github/workflows/android-apk.yml)，不需要本地安装 Android SDK。

1. Fork 或使用当前仓库，确认默认分支包含 `android/` 和此 workflow。
2. 在仓库 Settings → Secrets and variables → Actions 配置：
   - `AA_KEYSTORE_BASE64`：个人 keystore 的 base64；
   - `AA_KEYSTORE_PASSWORD`；
   - `AA_KEY_ALIAS`；
   - `AA_KEY_PASSWORD`。
3. 在 `android/app/build.gradle.kts` 修改 `versionCode` 和 `versionName`（每次发布必须递增 `versionCode`）。
4. 推送到 `main` 且改动包含 `android/**` 或手动运行 workflow。构建会产出 debug/release APK，
   并发布一个 `v2.1.1-tablet.<run_number>` 的 prerelease。
5. 下载 release APK 后核对包名、`versionCode`、`versionName` 和证书指纹，再分发。

本地开发可以运行 `android/README.md` 中的 Gradle 命令；正式交付以 Actions 产物为准。

## 部署 Server

沿用官方 Docker 部署方式，不需要新增环境变量：

```bash
POSTGRES_PASSWORD='replace-me' \
AGENT_SERVER_SECRET='replace-me-with-a-long-random-secret' \
docker compose -f docker/docker-compose.postgres.yml up --build
```

服务端使用 PostgreSQL 和 Redis；首次启动从日志读取 setup token 创建管理员。缩略图依赖会随
`server/uv.lock` 安装。升级前按 [升级指南](../upgrading.md) 处理数据库 revision，并备份
`/data` 中的附件目录。凭据、数据库密码、keystore、`local.properties` 和任何 `.env` 都不要提交。

## 与官方同步

```bash
git remote add upstream https://github.com/anywhere-labs/Agents-Anywhere.git
git fetch upstream
git switch feat/apk/tablet-adaptive-layout
git rebase upstream/main
```

个人改动尽量按主题保持独立提交（Android、Server、Bridge 分开），冲突时优先保留个人功能，同时重新跑
Android workflow 和服务端相关测试。发布新版本时同步更新本文件、`docs/releases/` 下的发布说明和根
`CHANGELOG.md`。

## 快速检查清单

- [ ] `android/app/build.gradle.kts` 的版本号和 `versionCode` 已递增。
- [ ] Actions 的四个签名 Secrets 存在，keystore 有离线备份。
- [ ] Release APK 的签名证书与上一版一致，能覆盖安装。
- [ ] Server 的 `server/uv.lock` 与 `pyproject.toml` 一致，附件目录有备份。
- [ ] 没有把密码、token、keystore、`local.properties` 或用户数据提交进 Git。
- [ ] 更新了 `docs/releases/` 与 `CHANGELOG.md`。
