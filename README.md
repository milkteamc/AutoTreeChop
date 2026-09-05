# AutoTreeChop

![atc-intro](https://github.com/user-attachments/assets/7b556970-7c4c-4271-9016-4a4612895379)  
**AutoTreeChop** lets your players chop entire trees by breaking just one log.
It's async-friendly, lightweight, and fully customizable — with built-in support for MySQL(optional), CoreProtect, and popular protection plugins.

- 🌐 [Matrix Support Chat](https://matrix.to/#/#maoyue-dev:matrix.org)
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

### 🗄️ MySQL & SQLite Support

- Scale with MySQL or keep it simple with SQLite (default)

---

## Translations

Automatically switches to the player's locale if enabled.

Bundled languages are discovered automatically from the plugin JAR's `lang/` folder.
To add a custom language, place a UTF-8 `<locale>.properties` file directly in
`plugins/AutoTreeChop/lang/` and run `/atc reload`. Both `pt-BR.properties` and
`pt_BR.properties` are accepted; use only one filename per locale. No Java changes are needed.
For bundled translations, add the file to `src/main/resources/lang/` and rebuild.

Startup and reload export missing bundled files, including `styles.properties`, without
replacing existing files. Missing bundled message keys are appended to matching user files;
customized values are preserved. Deleted bundled files are restored on reload, while deleted
custom-only languages are unloaded. `styles.properties` is reserved for message styling.
Invalid locale names or malformed language files are logged and skipped; duplicate normalized
locales use the first filename in lexicographic order and log a warning.

Player locales first match the exact locale, then the language-only file, then the configured
locale. Missing message keys retain the existing fallback order: selected locale, English,
configured locale, then any other loaded language containing the key.


<a href="https://translate.codeberg.org/engage/autotreechop/">
  <img src="https://translate.codeberg.org/widget/autotreechop/autotreechop/multi-auto.svg" alt="Translation Status">
</a>

---

## Supported Plugins
>
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
| `/atc confirm` | Confirm a pending chop (idle / no-leaves warning) |
| `/atc usage` | Show daily usage |
| `/atc reload` | Reload plugin config |
| `/atc toggle <player>` | Toggle for another player |
| `/atc enable <player/@a/@r/@p>` | Enable for other players |
| `/atc disable <player/@a/@r/@p>` | Disable for other players |
| `/atc about` | Plugin info |

---

## Permissions

> Requires a permission manager plugin, I personally recommend [LuckPerms](https://luckperms.net/download)

| Permission | Description | Default |
|------------|-------------|-------------|
| `autotreechop.use` | Use `/atc`, `/atc confirm`, and `/atc usage` commands | Everyone |
| `autotreechop.vip` | Ignore usage limits | OP |
| `autotreechop.other` | Toggle others' ATC status | OP |
| `autotreechop.reload` | Reload config file | OP |
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

## Developer API

Integrate through Bukkit ServicesManager to read player state and change the ATC preference.
See the [API guide](https://github.com/milkteamc/AutoTreeChop/blob/master/docs/API.md) for dependency setup, lifecycle and threading rules,
explicit operation results, and a compiled example plugin.

---

## Support & Contribute

- Need help? Join our [Matrix](https://matrix.to/#/#maoyue-dev:matrix.org)
- Found a bug? Open an issue on [GitHub](https://github.com/milkteamc/AutoTreeChop/issues)
- Want to help translate? Get started [on this page](https://translate.codeberg.org/projects/autotreechop/autotreechop)
- Love the plugin? Give it a ⭐️ on [GitHub](https://github.com/milkteamc/AutoTreeChop)

---

## bStats

This plugin uses [bStats](https://bstats.org) to collect anonymous usage statistics (such as plugin version, server software, and player count).  
These statistics help us improve the plugin.  
If you prefer not to participate, you can disable it anytime in:
`/plugins/bStats/config.yml`

[![bstats](https://bstats.org/signatures/bukkit/AutoTreeChop.svg)](https://bstats.org/plugin/bukkit/AutoTreeChop/20053)

---

## License

```txt
    Copyright (C) 2026 MilkTeaMC and contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
