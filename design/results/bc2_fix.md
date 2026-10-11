# BC2 fix: tiny-config for dev run

Problem: runClient crashed, Better Combat 2.4.0+1.21.1-fabric could not find net.tiny_config.ConfigManager (ClassNotFoundException via entrypoint 'main' bettercombat). tiny-config 3.1.0-fabric is nested in the BC jar (META-INF/jars), and Loom does not load nested jars in dev.

Fix: mod/build.gradle, added `modLocalRuntime "maven.modrinth:tiny-config:3.1.0-fabric"` next to the three BC deps.

Checks:
- Modrinth API: tiny-config 3.1.0-fabric exists (game versions 1.21-1.21.10). Newer 3.1.1-fabric exists too, but 3.1.0 matches the nested jar.
- Modrinth Maven: .pom 200, .jar 307 redirect (served).
- `./gradlew build -q`: exit 0, no error/FAILED/Exception.
- `dependencies --configuration runtimeClasspath`: resolves `remapped.maven.modrinth:tiny-config-652de5e3:3.1.0-fabric`.
- fabric.mod.json untouched: tiny-config is not needed in depends (dev only, our mod does not use it directly; the production BC jar carries it nested).

NOT run: runClient (left to the main agent). UNVERIFIED: that the crash is gone at runtime.
