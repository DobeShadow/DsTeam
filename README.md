# TeamPlugin

轻量级 Minecraft 临时组队插件，适用于 Paper 1.21+。纯内存运行，无数据库依赖。

## 功能

**组队系统**
- 创建/解散队伍，支持邀请、申请、踢出、离队
- 同一队伍内 PvP 保护（近战 + 弹射物），打试炼不误伤

**召集传送**
- 队长一键召集，队员在聊天框点击 `✔ 接受` 或 `✘ 拒绝`
- 支持 Title 提示，悬停显示操作说明

**GUI 界面**
- `/team gui` — 队伍面板：队友头像、血量、在线状态一目了然
- 队长面板：邀请玩家、召集队员、解散队伍 一键操作
- 邀请界面：54 格可翻页的在线玩家列表，点击头像直接邀请

**聊天框交互**
- 收到邀请/申请/召集时，弹出卡片式通知
- `✔` `✘` 按钮悬停有提示，点击即操作，无需手动输命令

## 命令

| 命令 | 简写 | 说明 |
|------|------|------|
| `/team create` | `/team c` | 创建队伍 |
| `/team invite <玩家>` | `/team inv` | 邀请玩家加入队伍（队长） |
| `/team accept <玩家>` | `/team acc` | 接受邀请或入队申请 |
| `/team deny <玩家>` | `/team den` | 拒绝邀请或入队申请 |
| `/team join <队长>` | `/team j` | 申请加入队伍 |
| `/team kick <玩家>` | `/team k` | 踢出队员（队长） |
| `/team leave` | `/team l` | 离开队伍 |
| `/team disband` | `/team dis` | 解散队伍（队长） |
| `/team summon` | `/team s` | 召集所有队员传送（队长） |
| `/team s a` | — | 接受召集传送 |
| `/team s d` | — | 拒绝召集传送 |
| `/team gui` | `/team g` | 打开队伍 GUI |
| `/team info` | `/team i` | 查看队伍信息 |
| `/team list` | — | 查看所有活跃队伍 |

别名：`/pteam`、`/pt`

## 使用流程

```
1. /team create          — 创建队伍，成为队长
2. /team gui → 邀请玩家   — 打开 GUI，翻页选人，点击即邀请
3. 对方聊天框点 ✔ 接受    — 加入队伍
4. /team s               — 打试炼前队长召集全员
5. 队员点 ✔ 接受召集      — 传送到队长身边
6. 打完后 /team disband   — 解散队伍
```

## 配置

```yaml
# config.yml
max-team-size: 6                       # 最大队伍人数（含队长）
invite-timeout: 60                     # 邀请/申请超时（秒）
summon-timeout: 30                     # 召集超时（秒）
chat-prefix: "&8[&b组队&8] &7"         # 系统消息前缀
summon-title: true                     # 召集时屏幕显示 Title
console-log: true                      # 控制台记录组队日志
```

## 安装

```bash
mvn package
# 将 target/TeamPlugin-1.0.0.jar 放入 plugins/ 目录
```

## 权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `teamplugin.use` | 所有人 | 使用组队命令 |
| `teamplugin.admin` | OP | 管理员（预留） |

## 构建

- Java 21
- Maven 3.9+
- Paper API 1.21.1-R0.1-SNAPSHOT

## 许可证

MIT
