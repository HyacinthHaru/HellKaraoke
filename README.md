# 孟婆汤（HellKaraoke）

A death music plugin for Minecraft.

玩家死亡后随机播放悠扬美丽动听的司凤音乐中的随机一首，让你的玩家必须听下去，听歌达到歌曲完整时长的 85% 后才能复活，支持 Java 版和通过 Geyser 接入的基岩版，

> 坏消息，你死了。
好消息，你需要听完这首歌才能复活。

## 运行环境

- Paper 1.21.8 API 构建。
- Java 21 或以上，具体版本以服务端要求为准。
- PacketEvents 2.14.0。
- 支持 Geyser

## 安装

1. 将 `HellKaraoke.jar` 和 PacketEvents 放入 Paper 服务端的 `plugins` 目录。
2. 配置下方的 Java、基岩版资源包。
3. 保持服务器的立即重生规则关闭，重启服务端后进入游戏。

死亡时从当前八首歌曲中独立随机选择，每首概率相同。下一次死亡会停止上一首歌曲，避免叠播。

Java 版在听完歌曲后刷新死亡菜单，随后手动点击重生。基岩版客户端可能自动重试重生，并在解锁后自动复活。

## 音乐列表

大东北我的家乡 司凤版

See You Again 司凤版

雨爱司凤版

云原神之歌 司凤版

朋友的酒 司凤版

忘情牛肉面 司凤版

大课间跑操音乐 司凤版

中国人能飞 司凤版



ps，随机播放，不分先后

## 资源包

资源包名称为 `the best songs i've heard`，嗯对，确实是我听到过的最动听的音乐。两端各约 5.60 MB，使用相同的歌曲和声音 ID。

| 平台 | 文件 |
| --- | --- |
| Java | [the best songs i've heard.zip](resource-pack/the%20best%20songs%20i%27ve%20heard.zip) |
| 基岩版 | [the best songs i've heard.mcpack](resource-pack/the%20best%20songs%20i%27ve%20heard.mcpack) |

### Java 版

将 ZIP 上传到可直接下载的地址，在 `server.properties` 中配置：

```properties
resource-pack=<Java 资源包下载直链>
require-resource-pack=true
```
更换资源包内容后，需要同步更新下载文件。

### 基岩版

将 MCPACK 放入 Geyser 自身的 `packs` 目录，保持 `force-resource-packs` 为 `true`，重载或重启 Geyser 后让玩家重新连接。

## 管理命令

| 命令 | 用途 |
| --- | --- |
| `/respawnprobe status [玩家]` | 查看当前歌曲、剩余等待时间和重生状态 |
| `/respawnprobe rescue [玩家]` | 解除等待限制并强制重生 |

权限为 `respawnprobe.admin`，默认授予 OP。

## 构建

```sh
mvn clean package
```

输出文件为 `target/HellKaraoke.jar`。

