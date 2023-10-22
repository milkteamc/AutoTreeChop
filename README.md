# AutoTreeChop
A auto tree chopping plugin for milkteamc.  
Discord: https://discord.gg/pyNS5xAvMs   
## Permission and commands
usage: `/autotreechop`  
description: Toggle auto tree chop.  
permission: `autotreechop.use`  
## Config
`config.yml`
```yml
name: AutoTreeChop
version: '${version}'
main: org.milkteamc.autotreechop.AutoTreeChop
api-version: 1.17
author: Maoyue
description: A auto tree chopping plugin for milkteamc

commands:
  autotreechop:
    description: Toggle auto tree chop.
    usage: /autotreechop
    permission: autotreechop.use

permissions:
  autotreechop.use:
    description: Allows players to use the auto tree chop command.
    default: true
  autotreechop.vip:
    description: VIP permission can ignore all limit.
    default: op
```
![bstats](https://bstats.org/signatures/bukkit/AutoTreeChop.svg)