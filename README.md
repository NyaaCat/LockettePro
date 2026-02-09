# LockettePro [![Build Status](https://ci.nyaacat.com/job/LockettePro/job/main/badge/icon?style=flat-square)](https://ci.nyaacat.com/job/LockettePro/job/main/)

<!-- For Alerts: https://docs.github.com/en/get-started/writing-on-github/getting-started-with-writing-and-formatting-on-github/basic-writing-and-formatting-syntax#alerts -->
> [!CAUTION]\
> ***Versions for Minecraft 1.21 are not fully tested yet.***  
> Please create an issue if you find anything wrong. Thank you.  

All versions can be found at [Nyaa CI server](https://ci.nyaacat.com/job/LockettePro/) 
For the newest build on main branch, click [here](https://ci.nyaacat.com/job/LockettePro/job/main/lastSuccessfulBuild/)

### Version history
- 2.9.15 : Minecraft 1.21, downloadable from [github actions](https://github.com/NyaaCat/LockettePro/actions/workflows/autobuild.yml)
- 2.9.x-1.15.1: Minecraft 1.15.1, since build 10
- 2.9.x-1.14.4: Minecraft 1.14.4, until build 9

English documentation: `README.md`  
中文说明: `README.zh.md`

## What LockettePro does
LockettePro protects blocks by validating ownership and access before players, hoppers, mobs, explosions, and redstone can interact with them.

It supports two lock models:

- Sign lock (classic Lockette behavior): a wall sign near the protected block defines owner/users/tags.
- PDC lock (container-only): lock data is stored inside the container PersistentDataContainer (no visible sign required).

Both models are available at the same time.

## Core runtime logic
For containers (chest, barrel, hopper, furnace, etc.):

1. If LockettePro PDC lock data exists, LockettePro uses PDC permissions directly.
2. If no PDC lock data exists, LockettePro falls back to sign scanning.
3. Effective container lock state is mirrored to a configurable key (`locked-container-pdc-key`, default `lockettepro:locked_container`) for plugin/server integration.

For non-container lockables (for example doors or decorative lockables such as `DIAMOND_BLOCK`), protection remains sign-based.

This means:

- PDC locks are fast and do not require nearby signs.
- Sign locks still work for every configured lockable, including non-container blocks.
- Containers with PDC lock data skip sign lookup for permission checks.

## Installation (Admin)
1. Download the plugin jar.
2. Put it in `plugins/`.
3. Start or restart the server.
4. Edit `plugins/LockettePro/config.yml` as needed.
5. Run `/lock reload` after changes.

Optional integrations (soft-depend):

- Vault
- WorldGuard
- ProtocolLib
- CoreProtect

## Configuration guide (Admin)
Main options in `config.yml`:

- `enable-quick-protect`: quick-lock mode (`true`, `false`, `sneak`).
- `lockables`: what can be protected.
- `lockables` supports both material names and block tags such as `minecraft:doors`.
- Prefix with `-` in `lockables` to exclude a material/tag from the current set.
- `block-item-transfer-in` / `block-item-transfer-out`: block hopper-like transfer into/out of locked containers.
- `block-item-transfer-cooldown-ticks`: cooldown added to blocked hoppers to reduce retry spam.
- `container-bypass-sign-tags`: tags (for example `[hopper]`) that make container transfer restrictions bypassable.
- `everyone-signs`: tags that make access open to everyone (for example `[everyone]`).
- `locked-container-pdc-key`: PDC key used as an external "effectively locked container" marker.
- `permission-groups-file`: persistent file for permission groups (default `permission-groups.json`).
- `permission-groups-autosave-seconds`: autosave interval for permission groups.

## Player usage
### Sign lock workflow
- Quick protect: right-click a lockable block with a sign item (if enabled by config).
- Manual protect: place a wall sign with `[Private]` on line 1.
- Add extra users/tags: use `[More Users]` sign.
- Common tags: `[everyone]` (everyone can use), `[hopper]` (container transfer bypass tag).
- Edit lock sign text: right-click a lock sign to select it, then run `/lock <1|2|3|4> <text>`.

### PDC lock workflow (container only)
- `/lock on`  
Lock the targeted container and set yourself as owner.
- If the container is currently sign-locked and you are owner, this creates PDC lock data and future access checks prefer PDC data.
- Existing sign text is not auto-imported into PDC permissions.

- `/lock info`  
Show current owners, permission entries, and `locked_container` state of targeted container.

- `/lock rename <new name>`  
Rename targeted PDC-locked container (owner only). Supports color format such as `&#rrggbb`.

- `/lock permission <node>`  
Edit targeted container permissions (owner only).

Permission node format:

- `<mode>:<subject>`
- modes: `xx` (owner), `rw` (read/write), `ro` (read-only), `--` (remove)
- subjects: player UUID or player name (online names normalize to UUID), `[tag]`, `#entity`, or `[g:GroupName]`
- tag examples: `[everyone]`, permission group tags, scoreboard team tags
- entity example: `#hopper`

Examples:

- `xx:AdminPlayer`
- `rw:FriendA`
- `ro:[everyone]`
- `rw:#hopper`
- `rw:[g:BaseMembers]`
- `--:FriendA`

### Permission clone tool
- `/lock clone` gives a special item containing current container permissions (+ custom name).
- Right-click another container with that item to copy permissions quickly.
- Target rules: unlocked container (allowed), PDC-locked container (owner required), sign-locked container (owner required)

## Permission groups (Player-owned)
Permission groups reduce long permission lists in PDC locks.

- Each player can own one group.
- Group name must be unique server-wide.
- Only the owner can edit/delete the group.
- Group file is loaded on startup and autosaved periodically.

Commands:

- `/lock group create <groupName>`
- `/lock group delete <groupName>`
- `/lock group add <groupName> <subject>`
- `/lock group remove <groupName> <subject>`
- `/lock group info <groupName>`
- `/lock group list`

Use group in container permission:

- `/lock permission rw:[g:<groupName>]`

## Permissions
Common permission nodes:

- `lockettepro.command`: use `/lock` base command
- `lockettepro.lock`: create sign locks / quick lock
- `lockettepro.edit`: sign edit workflow (`/lock 1..4`)
- `lockettepro.pdc.on`
- `lockettepro.pdc.info`
- `lockettepro.pdc.rename`
- `lockettepro.pdc.permission`
- `lockettepro.pdc.clone`
- `lockettepro.pdc.group`
- `lockettepro.pdc.*`: includes all PDC/group sub-permissions
- `lockettepro.reload`: reload config
- `lockettepro.version`: view plugin version

Additional admin/legacy nodes used by runtime checks:

- `lockettepro.lockothers`
- `lockettepro.noexpire`
- `lockettepro.admin.edit`
- `lockettepro.edit.admin`
- `lockettepro.admin.break`
- `lockettepro.admin.use`
- `lockettepro.admin.interfere`

For survival/RPG/adventure setups, explicitly grant PDC permissions only to roles that are allowed to create and manage virtual locks.

## Notes and compatibility
- Quick-lock sign placement now fires placement event checks, so region/anti-grief plugins can block it consistently.
- Blocked hopper transfers apply a cooldown to reduce repeated checks.
- PDC lock currently applies to containers only.
- Non-container lockables remain sign-only by design.
- Sign lock and PDC lock can coexist in the same server; container access checks prefer PDC when present.
