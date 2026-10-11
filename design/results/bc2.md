# BC2: Better Combat as a dependency (2026-10-11)

Only build config and metadata changed. No BC code/assets/jar copied, no `include`, no Java changes.

## Added lines
`mod/build.gradle`, `repositories {}`:
```groovy
exclusiveContent {
	forRepository { maven { name = "Modrinth"; url = "https://api.modrinth.com/maven" } }
	filter { includeGroup "maven.modrinth" }
}
```
`dependencies {}` (after fabric-api):
```groovy
modLocalRuntime "maven.modrinth:better-combat:2.4.0+1.21.1-fabric"
modLocalRuntime "maven.modrinth:playeranimator:2.0.4+1.21.1-fabric"
modLocalRuntime "maven.modrinth:cloth-config:15.0.140+fabric"
```
`mod/src/main/resources/fabric.mod.json` `depends` (there was no `suggests` block in the file, nothing to replace):
`"bettercombat": ">=2.4.0"`, `"playeranimator": ">=2.0.0"`, `"cloth-config": ">=15.0.130"`.

## Verified
- `modLocalRuntime` works with Loom (fabric-loom-remap 1.17.21), no fallback to `modImplementation` needed.
- `./gradlew build --console=plain -q` (JDK 21.0.12, GRADLE_USER_HOME=D:/gradle-home): exit 0, no `error:|FAILED|Exception`. Tests passed as part of build.
- Jars downloaded from Modrinth maven into the Gradle cache; BC SHA-1 `38d1d1f22e9255092baf85abf93a3b9746fa6ed1` matches bc_check.
- `dependencies --configuration runtimeClasspath` lists `remapped.maven.modrinth:better-combat-652de5e3:2.4.0+1.21.1-fabric`, `playeranimator-652de5e3:2.0.4+1.21.1-fabric`, `cloth-config-652de5e3:15.0.140+fabric` (remapped, resolved). The configuration `modRuntimeClasspath` named in the task does not exist in this Loom (error), runtimeClasspath is the right one.
- Built `build/libs/reiatsu_test-0.0.1-spike.jar`: 0 entries from bettercombat/playeranimator/cloth, no META-INF/jars; its fabric.mod.json carries the new depends.

## UNVERIFIED
- Real client and server start with both mods (Loader dependency check, mixin conflicts, MixinExtras, playerAnimator 2.0.4 beta with BC 2.4.0): the game was not run.
- Consequence: the built mod now REQUIRES BC, playerAnimator and Cloth Config at install time; a plain jar without them will not load (as decided, ARR: author permission for public release still open).
- Dedicated-server run (`runServer`) classpath also gets BC, not tried.
