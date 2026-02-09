# LockettePro [![Build Status](https://ci.nyaacat.com/job/LockettePro/job/main/badge/icon?style=flat-square)](https://ci.nyaacat.com/job/LockettePro/job/main/)

> [!CAUTION]\
> ***Minecraft 1.21 版本目前仍未完全测试。***  
> 如果你遇到问题，请提交 issue。谢谢。  

所有构建可在 [Nyaa CI server](https://ci.nyaacat.com/job/LockettePro/) 下载。  
main 分支最新成功构建请点 [here](https://ci.nyaacat.com/job/LockettePro/job/main/lastSuccessfulBuild/)。

### Version history
- 2.9.15 : Minecraft 1.21, downloadable from [github actions](https://github.com/NyaaCat/LockettePro/actions/workflows/autobuild.yml)
- 2.9.x-1.15.1: Minecraft 1.15.1, since build 10
- 2.9.x-1.14.4: Minecraft 1.14.4, until build 9

## 插件做什么
LockettePro 用“权限校验 + 事件拦截”的方式保护方块，避免未授权玩家、漏斗、红石、生物、爆炸等破坏或访问被锁内容。

当前支持两套锁机制：

- 牌子锁：经典 Lockette 逻辑，权限写在方块旁边的牌子上。
- PDC 锁（仅容器）：权限写入容器本体的 PersistentDataContainer，不需要外部牌子。

## 核心判定逻辑
对于容器（箱子、木桶、熔炉、漏斗等）：

1. 如果存在 LockettePro 的 PDC 锁数据，优先使用 PDC 权限判定。
2. 如果不存在 PDC 锁数据，回退到牌子锁判定。
3. 有效锁状态会同步到 `locked-container-pdc-key`（默认 `lockettepro:locked_container`），便于与其他插件或服务端逻辑联动。

对于非容器可锁方块（例如门、`DIAMOND_BLOCK`）：

- 继续走牌子锁逻辑，不使用 PDC 锁。

## 安装（管理员）
1. 下载插件 jar。
2. 放入 `plugins/` 目录。
3. 启动/重启服务器。
4. 修改 `plugins/LockettePro/config.yml`。
5. 配置修改后执行 `/lock reload`。

可选软依赖：

- Vault
- WorldGuard
- ProtocolLib
- CoreProtect

## 配置重点（管理员）
`config.yml` 常用项：

- `enable-quick-protect`：快捷上锁模式（`true`、`false`、`sneak`）。
- `lockables`：可锁方块列表。
- `lockables` 支持材质名与方块标签，如 `minecraft:doors`。
- `lockables` 中前缀 `-` 可从当前集合里移除一个材质/标签。
- `block-item-transfer-in` / `block-item-transfer-out`：是否阻止对锁容器的自动传输。
- `block-item-transfer-cooldown-ticks`：阻止传输后给漏斗加冷却，减少无效重试。
- `container-bypass-sign-tags`：容器传输绕过标签（如 `[hopper]`）。
- `everyone-signs`：全员访问标签（如 `[everyone]`）。
- `locked-container-pdc-key`：容器有效锁状态标记 key。
- `permission-groups-file`：权限组持久化文件名（默认 `permission-groups.json`）。
- `permission-groups-autosave-seconds`：权限组自动保存间隔。

## 玩家用法
### 牌子锁
- 快捷上锁：手持牌子右键可锁方块（取决于 `enable-quick-protect`）。
- 手动上锁：在墙牌首行写 `[Private]`。
- 添加额外权限：使用 `[More Users]` 牌子。
- 常用标签：`[everyone]`（允许所有玩家访问）、`[hopper]`（允许容器自动传输绕过限制）。
- 修改牌子文本：先右键选中锁牌，再执行 `/lock <1|2|3|4> <text>`。

### PDC 锁（仅容器）
- `/lock on`  
给当前准星容器上锁，并把自己设为 owner。
- 如果该容器原本是牌子锁且你是 owner，后续会优先按 PDC 权限判定。
- 现有牌子文本不会自动导入 PDC 权限，需要用 `/lock permission` 或 `/lock clone` 重新整理。

- `/lock info`  
查看当前容器 owner、权限列表、`locked_container` 状态。

- `/lock rename <新名称>`  
修改容器名字（仅 owner）。支持 `&#rrggbb` 颜色格式。

- `/lock permission <节点>`  
修改容器权限（仅 owner）。

权限节点格式：

- `<mode>:<subject>`
- `mode`：`xx` owner、`rw` 可读写、`ro` 只读、`--` 删除该 subject 权限
- `subject`：玩家 UUID/玩家名、`[tag]`、`#entity` 或 `[g:组名]`
- `tag` 示例：`[everyone]`、权限组标签、计分板队伍标签
- `#entity` 示例：`#hopper`

示例：

- `xx:AdminPlayer`
- `rw:FriendA`
- `ro:[everyone]`
- `rw:#hopper`
- `rw:[g:BaseMembers]`
- `--:FriendA`

### 快速复制权限
- `/lock clone` 会给你一个“权限复制工具”物品（带容器名称和权限数据）。
- 用这个物品右键其他容器，可快速覆盖权限。
- 目标容器规则：未上锁（可直接应用）、PDC 已上锁（需 owner）、牌子已上锁（需 owner）。

## 权限组（玩家自有）
权限组用于减少单容器上写大量权限节点的需求。

- 每位玩家默认只能拥有一个组。
- 组名全服唯一。
- 只有组拥有者可修改/删除。
- 数据在服务器启动时加载进内存，并按配置周期自动落盘。

命令：

- `/lock group create <groupName>`
- `/lock group delete <groupName>`
- `/lock group add <groupName> <subject>`
- `/lock group remove <groupName> <subject>`
- `/lock group info <groupName>`
- `/lock group list`

在容器权限中使用组：

- `/lock permission rw:[g:<groupName>]`

## 权限节点（管理员）
常用节点：

- `lockettepro.command`
- `lockettepro.lock`
- `lockettepro.edit`
- `lockettepro.pdc.on`
- `lockettepro.pdc.info`
- `lockettepro.pdc.rename`
- `lockettepro.pdc.permission`
- `lockettepro.pdc.clone`
- `lockettepro.pdc.group`
- `lockettepro.pdc.*`
- `lockettepro.reload`
- `lockettepro.version`

运行期还会用到的附加节点（偏管理/兼容）：

- `lockettepro.lockothers`
- `lockettepro.noexpire`
- `lockettepro.admin.edit`
- `lockettepro.edit.admin`
- `lockettepro.admin.break`
- `lockettepro.admin.use`
- `lockettepro.admin.interfere`

如果你的服务器是 RPG/冒险模式，建议只给特定角色发放 `lockettepro.pdc.*` 或其子节点，避免玩家在不需要放置牌子的场景里滥用虚拟锁。

## 兼容与注意事项
- 快捷上锁会触发放置事件，区域保护/反破坏插件可正常拦截。
- 漏斗被锁阻止时会施加冷却，降低高频无效查询开销。
- PDC 锁目前仅支持容器。
- 门和普通方块仍然使用牌子锁。
- 同时存在牌子锁和 PDC 锁时，容器权限判定优先使用 PDC 数据。
