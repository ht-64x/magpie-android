# magpie for Android

把 magpie 的本地 AI 网关装进手机：**手机里每个 AI App 只连一个本地地址，真实厂商密钥永不外泄。**

配套控制台、API 管理、WorkBuddy 渠道、每日自动签到，全部在同一个 App 里，不需要电脑。

---

## 为什么值得装：本地 API 是核心

平时把 DeepSeek / Kimi / GLM / OpenRouter 的 key 填进每个 App，等于把密钥复制到十几处：任何一个 App 泄漏、被备份、被导出，key 就没了。

装了这个 App 之后：

1. 真实密钥只存在手机里的 magpie 配置里（应用私有目录，不进相册、不进备份）；
2. 每个 AI App 填的是 **http://127.0.0.1:3425/v1** + 一把**只属于它自己的本地密钥**；
3. 某个 App 不用了，在「API」页把它的密钥删掉即可，其它 App 不受影响；
4. 密钥可以随时轮换（旧密钥立刻失效）、停用、改名，全部在手机上点两下完成。

换句话说：**同一台手机上，一个本地端点接所有 AI App，密钥一处管理、随时吊销。**

## 主要功能

- **本地 OpenAI 兼容 API**：`/v1/chat/completions`、`/v1/responses`、`/v1/models`，另有 Anthropic `/v1/messages`、Gemini `/v1beta`、`/v1/embeddings`、`/v1/rerank`。App 不用改代码，只改 base_url。
- **API 分栏（专为手机做的管理页）**：每把密钥一张卡，显示启用状态、测试结果（真实发一次 /v1/models 验证连通），支持复制/重命名/停用/轮换/删除。
- **多供应商 + 路由**：控制台里加任意 provider（官方 API、中转、本地 Ollama/LM Studio），额度用完按路由组自动换下一个，客户端看不到失败。
- **WorkBuddy 渠道**：供应商包里自带，登录后即可用它的免费窗口；每天 09:05 自动签到（也可手动），卡片底部一个开关。
- **一个开关控制整个网关**：顶栏滑动开关，随时停掉本地服务（停掉即端口关闭，任何 App 都连不上）。
- **用量与日志**：请求级日志（模型、供应商、状态码、耗时），内置「错误摘要 / 导出日志」，排障不用连电脑。
- **手机原生体验**：手机字号与间距、无「更多」空页、无赞助广告位、桌面字眼全部替换为手机说法。

## 安全模型

- 网关默认只监听 `127.0.0.1`：**只有本机能连**，路由器、隔壁电脑都访问不到；
- 需要给平板/电脑用时，在「监听」里打开局域网（`0.0.0.0`），并先建一把密钥；
- 每把密钥可单独停用/删除/轮换，不必改任何 App 里的厂商 key；
- App 不内置任何账号或密钥，仓库与安装包都不含个人数据。

## 安装

下载 Release 里的 `magpie-lite.apk`（arm64-v8a，Android 8+），或自行构建：

    adb install -r magpie-lite.apk

首次使用：

1. 打开 App，顶栏开关打开网关；
2. 控制台 → 供应商 → 添加你的 provider（或用 WorkBuddy，登录后自动出现）；
3. 底部「API」→ 新建一把密钥给某个 App 用；
4. 在该 App 里填 base_url `http://127.0.0.1:3425/v1`，key 填刚建的那把。

## 自行构建

    git clone --depth 1 https://github.com/yetone/magpie ../magpie-src
    cd ../magpie-src
    set GOOS=android & set GOARCH=arm64 & set CGO_ENABLED=0
    go build -tags nogui -trimpath -ldflags "-s -w" -o dist/magpie-android-arm64 .
    cd ../magpie-android
    scripts/fetch-preload.ps1              # 可选：Bun + WorkBuddy 包
    scripts/build-apk.ps1 -Sdk C:\Android\sdk -CoreSo ../magpie-src/dist/magpie-android-arm64

依赖：JDK 17、Android SDK build-tools 34 + platform 34、Go 1.26。

## 说明

- Android 没有 `/etc/resolv.conf`，App 会把系统 DNS 通过 `MAGPIE_DNS` 传给 Go 核心；
- Bun 以 `libbun.so` 形式打包（`nativeLibraryDir` 是安卓唯一允许 exec 的位置）；
- 本项目只做壳与手机层，网关本身来自 [magpie](https://github.com/yetone/magpie)（MIT, by yetone）。

MIT.
