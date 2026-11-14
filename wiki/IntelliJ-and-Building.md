# IntelliJ IDEA and building

[Wiki home](Home.md) · [Troubleshooting](Troubleshooting.md)

## Open the project

Use IntelliJ IDEA's **Get from VCS** with:

```text
https://github.com/ItsRileyNJ/Fantasy-Feudalism-Administration.git
```

Open the repository root as a Gradle project and let dependency/mapping synchronization complete. The project uses its included Gradle wrapper.

Set the **Project SDK to Java 21**. Under **Settings → Build, Execution, Deployment → Build Tools → Gradle**, select the wrapper and a **Java 21 Gradle JVM**, such as Temurin 21. The Gradle JVM matters even if the editor's Project SDK is correct.

## Update and build the mod

1. Select the `main` branch and use **Git → Pull**. Retain your own uncommitted work; do not discard changes just to follow a tutorial.
2. Reload/sync the Gradle project when IntelliJ offers **Load Gradle Changes** or **Reload All Gradle Projects**.
3. In the Gradle tool window, run **Tasks → build → clean**, then **Tasks → build → build**. Use Gradle's task runner for the distributable JAR.
4. Find the result in:

   ```text
   build/libs/fantasy-feudalism-administration-0.7.0.jar
   ```

5. Stop your separate server, replace its previous FFA JAR, and restart. Rebuilding alone does not update an already running server.

`clean` removes older build outputs; it does not act as a world/configuration reset. Do not install multiple FFA versions simultaneously.

## Run checks from IntelliJ

Use **Execute Gradle Task** in the Gradle tool window if a custom task is not visible in the tree:

| Task/input | Purpose |
| --- | --- |
| `policyTest` | Run the standalone policy/persistence/parser assertions |
| `check` | Run the configured checks, including policyTest |
| `build` | Build the mod and run checks |
| `runGameTestServer` | Run the opt-in headless Minecraft regression suite |
| `runServer` | Launch the generated development server |

A normal development server follows Minecraft's own EULA/setup prompts. The generated run's working directory is normally `<project>/run`; inspect the run configuration if yours differs. Its settings/world paths are relative to that directory, not automatically your external live server.

A development run loads project classes. Do not also put another FFA JAR in its `run/mods` folder, which can create a duplicate mod ID.

GameTests create/change a disposable test world. Keep their run directory separate from production world data.

## Test compatible mods locally

Put the desired third-party server JARs and **their required dependencies** in a test folder, then execute:

```text
runGameTestServer -PcompatModsDir=C:/Minecraft/test-mods
```

Use a suitable path on your machine; forward slashes are convenient on Windows. Do not include another copy of FFA or unrelated client-only mods. Uploaded third-party JARs are not distributed in this repository.

The baseline CI matrix runs on Windows and Ubuntu. The development build was also checked locally with the supplied Mercenaries and Easy NPC core/UI files. That does not replace verification of your exact complete modpack and real clients.

## Capture a failure properly

In Execute Gradle Task, run:

```text
policyTest --stacktrace --console=plain
```

Copy the full relevant console output, including the first exception before Gradle's final failure summary. A path to `java.exe` is normally just the process used, not proof that the JDK is broken. For a server failure, include the FFA loaded version and the first cause in its log.
