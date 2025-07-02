# AutoTreeChop

## All introduction are in Modrinth page

A auto tree chopping plugin for milkteamc.  
Discord: https://discord.gg/uQ4UXANnP2  
Modrinth page: https://modrinth.com/plugin/autotreechop  
Dev build: https://github.com/milkteamc/AutoTreeChop/releases/
![bstats](https://bstats.org/signatures/bukkit/AutoTreeChop.svg)
* * *

## API

### Maven

```xml
<repository>
		  <id>jitpack.io</id>
	   <url>https://jitpack.io</url>
</repository>
```

```xml
<dependency>
	   <groupId>com.github.milkteamc</groupId>
	   <artifactId>AutoTreeChop</artifactId>
	   <version>master-SNAPSHOT</version>
</dependency>
```

### Gradle

```groovy
repositories {
	   maven { url 'https://jitpack.io' }
}
```

```groovy
dependencies {
	   implementation 'com.github.milkteamc:AutoTreeChop:master-SNAPSHOT'
}
```

[AutoTreeChopAPI.java](https://github.com/milkteamc/AutoTreeChop/blob/master/src/main/java/org/milkteamc/autotreechop/AutoTreeChopAPI.java)

## Building with Maven

Run `mvn package` to build the plugin using Maven.

## Fork Changes

This fork adds optional integrations and build improvements:

- A new `pom.xml` allows the plugin to be built with Maven alongside the existing Gradle build.
- `McMMO` support ensures only naturally generated logs are chopped so player-built structures remain safe when CoreProtect isn't available.
- `CoreProtect` is used to both log block removals and detect player placed blocks, allowing administrators to roll back changes if necessary.
- All integration logic lives in new classes under the `hooks` package to reduce changes to original files.
- Optional `Drop2Inventory-Plus` support sends chopped drops directly to player inventories.

