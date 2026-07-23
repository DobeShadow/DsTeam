# TeamPlugin

轻量级 Minecraft 临时组队插件，适用于 Paper 1.21+。

## 功能

- **临时组队** — 创建/解散队伍，纯内存运作，无数据库依赖
- **PvP 保护** — 同队玩家无法互相攻击（含近战和弹射物）
- **邀请 & 申请** — 队长可邀请玩家，玩家也可主动申请加入
- **召集传送** — 队长一键召集，队员点击聊天消息即可接受/拒绝
- **GUI 界面** — `/t g` 打开队伍面板，显示队友头像和快捷操作
- **悬停按钮** — 邀请/召集消息支持鼠标悬停提示 + 点击操作

## 命令

| 命令 | 简写 | 说明 |
|------|------|------|
| `/t create` | `/t c` | 创建队伍 |
| `/t invite <玩家>` | `/t inv` | 邀请玩家（队长） |
| `/t accept <玩家>` | `/t acc` | 接受邀请/申请 |
| `/t deny <玩家>` | `/t den` | 拒绝邀请/申请 |
| `/t join <队长>` | `/t j` | 申请加入队伍 |
| `/t kick <玩家>` | `/t k` | 踢出队员（队长） |
| `/t leave` | `/t l` | 离开队伍 |
| `/t disband` | `/t dis` | 解散队伍（队长） |
| `/t summon` | `/t s` | 召集队员传送（队长） |
| `/t s a` | — | 接受召集 |
| `/t s d` | — | 拒绝召集 |
| `/t gui` | `/t g` | 打开队伍 GUI |
| `/t info` | `/t i` | 查看队伍信息 |
| `/t list` | — | 查看所有队伍 |

别名：`/pteam`、`/pt`

## 配置

```yaml
# config.yml
max-team-size: 6        # 最大队伍人数
invite-timeout: 60      # 邀请超时（秒）
summon-timeout: 30      # 召集超时（秒）
chat-prefix: "&8[&b组队&8] &7"
summon-title: true      # 召集时显示 Title
console-log: true       # 控制台日志
```

## 安装

1. 使用 Maven 构建：`mvn package`
2. 将 `target/TeamPlugin-1.0.0.jar` 放入服务器 `plugins/` 目录
3. 重启服务器

## 权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `teamplugin.use` | 所有人 | 使用所有组队命令 |
| `teamplugin.admin` | OP | 管理员权限（预留） |

## 构建要求

- Java 21
- Maven 3.9+
- Paper API 1.21.1

## 许可证

MIT
