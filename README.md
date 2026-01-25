# AutoTreeChop
![atc-intro](https://github.com/user-attachments/assets/7b556970-7c4c-4271-9016-4a4612895379)  
**AutoTreeChop** lets your players chop entire trees by breaking just one log.
It’s async-friendly, lightweight, and fully customizable — with built-in support for MySQL(optional), CoreProtect, and popular protection plugins.

- 🌐 [Discord Support Server](https://discord.gg/uQ4UXANnP2)
- 🌱 [Modrinth Page](https://modrinth.com/plugin/autotreechop)
- 💻 [Source Code (GitHub)](https://github.com/milkteamc/AutoTreeChop)
- ⚙️ [Default Config](https://github.com/milkteamc/AutoTreeChop/blob/master/src/main/resources/config.yml)

---

## Key Features

### 🌲 Smart Tree Chopping
- Chop entire trees by breaking just one log
- Toggle on/off with `/atc` command or by sneaking (pressing SHIFT)
- Async support for smooth performance on Modern servers
- Customizable leaves cleaner

### ⚡ Lightweight & Easy to Configure
- Minimal performance impact
- Simple setup, and user-friendly configuration

### 🔁 Auto Replanting
- Automatically replant saplings after chopping
- Optionally require players to have saplings

### 🧑‍🤝‍🧑 Player Control & Limits
- Daily limits for usage and chopped blocks
- Configurable cooldowns
- VIP players can bypass limits with permission

### 🛡️ Full Protection Plugin Support
- Compatible with Residence, WorldGuard, Lands, GriefPrevention
- Supports **CoreProtect** for logging actions

### 🌐 Multi-Language & Locale Support
- Translations included: `en`, `zh`, `ja`, `de`, `es`, `fr`, `ru`, etc.
- Automatically switches to player’s locale if enabled

### 🗄️ MySQL & SQLite Support
- Scale with MySQL or keep it simple with SQLite (default)

---

## Supported Plugins
> Since we call the block break event directly by default, plugins such as CoreProtect and Drop2Inventory should be supported without modification.

- WorldGuard
- Residence
- Lands
- GriefPrevention
- PlaceholderAPI

---

## Commands

| Command | Description |
|--------|-------------|
| `/atc` | Toggle AutoTreeChop |
| `/atc usage` | Show daily usage |
| `/atc reload` | Reload plugin config |
| `/atc toggle <player/@a/@r/@p>` | Toggle for another player |
| `/atc enable <player/@a/@r/@p>` | Enable for another players |
| `/atc disable <player/@a/@r/@p>` | Disable for another players |
| `/atc about` | Plugin info |

---

## Permissions

> Requires a permission manager plugin, I personally recommend [LuckPerms](https://luckperms.net/download)

| Permission | Description | Default |
|------------|-------------|-------------|
| `autotreechop.use` | Use `/atc` and `/atc usage` commands | Everyone |
| `autotreechop.vip` | Ignore usage limits | OP |
| `autotreechop.other` | Toggle others' ATC status | OP |
| `autotreechop.updatechecker` | Receive update notifications | OP |
| `autotreechop.replant` | Enable auto replanting | Everyone |
| `autotreechop.leaves` | Enable leaves removal | Everyone |

---

## Placeholders

> Requires [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)

| Placeholder | Output |
|------------|--------|
| `%autotreechop_status%` | Whether ATC is enabled |
| `%autotreechop_daily_uses%` | Times used today |
| `%autotreechop_daily_blocks_broken%` | Tree blocks chopped today |

---

## Support & Contribute

- Need help? Join our [Discord](https://discord.gg/uQ4UXANnP2)
- Found a bug? Open an issue on [GitHub](https://github.com/milkteamc/AutoTreeChop/issues)
- Want to help translate? Get started [here](https://translate.codeberg.org/projects/autotreechop/autotreechop)
- Love the plugin? Give it a ⭐️ on [GitHub](https://github.com/milkteamc/AutoTreeChop)

---

## bStats
This plugin uses [bStats](https://bstats.org) to collect anonymous usage statistics (such as plugin version, server software, and player count).  
These statistics help us improve the plugin.  
If you prefer not to participate, you can disable it anytime in:
`/plugins/bStats/config.yml`

[![bstats](https://bstats.org/signatures/bukkit/AutoTreeChop.svg)](https://bstats.org/plugin/bukkit/AutoTreeChop/20053)
