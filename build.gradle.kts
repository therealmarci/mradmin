plugins {
    java
    id("com.modrinth.minotaur") version "2.10.0"
    // Packt den Socket.IO-Client (Uptime Kuma) in die JAR - auf dem Server muss nichts nachgeladen werden.
    id("com.gradleup.shadow") version "9.6.1"
    // Baut die Weboberfläche (Svelte, Ordner web/) - lädt Node selbst herunter, auf dem Rechner muss nichts installiert sein.
    id("com.github.node-gradle.node") version "7.1.0"
}

group = "at.marcelr"

// Versionen kommen aus gradle.properties: "version" ist die Plugin-Version (SemVer), "mcVersion" die
// Minecraft-Version dieses Branches. Veröffentlicht wird beides zusammen, z.B. "mc26.3-v1.2.0".
val pluginVersion = project.version.toString()
val mcVersion = providers.gradleProperty("mcVersion").get()
val paperApi = providers.gradleProperty("paperApi").get()
version = "mc$mcVersion-v$pluginVersion"

val modrinthProjectId = providers.gradleProperty("modrinthProjectId").get()
val githubRepository = providers.gradleProperty("githubRepository").get()
val socketIoVersion = providers.gradleProperty("socketIoVersion").get()
val hikariVersion = providers.gradleProperty("hikariVersion").get()
val sqliteVersion = providers.gradleProperty("sqliteVersion").get()
val mariadbVersion = providers.gradleProperty("mariadbVersion").get()
val supportedGameVersions = providers.gradleProperty("supportedGameVersions").get()
val isPrerelease = pluginVersion.let { it.startsWith("0.") || it.contains("-") }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    // Optional: Rechte schon vor dem Join prüfen (Wartungsmodus). Ohne LuckPerms wird erst beim Join gekickt.
    compileOnly("net.luckperms:api:5.5")
    // Uptime-Kuma-Anbindung (Socket.IO). Steckt mit in der JAR (umbenannt nach at.marcelr.mradmin.lib, siehe shadowJar).
    implementation("io.socket:socket.io-client:$socketIoVersion")
    // Datenbank: nur zum Kompilieren - auf dem Server lädt Paper sie über "libraries" in der plugin.yml.
    compileOnly("com.zaxxer:HikariCP:$hikariVersion")

    // Für die Tests des Uptime-Kuma-Parsers (auf dem Server bringt Paper Gson mit).
    testImplementation("com.google.code.gson:gson:2.13.1")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Unit-Tests für die server-unabhängige Logik (src/test/java). Laufen bei jedem "build".
tasks.test {
    useJUnitPlatform()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    val props = mapOf(
        // In der plugin.yml als SemVer mit Build-Info ("1.2.0+mc26.3"), weil Paper selbst ein "v" davorsetzt.
        "version" to "$pluginVersion+mc$mcVersion",
        "pluginVersion" to pluginVersion,
        "mcVersion" to mcVersion,
        "modrinthProjectId" to modrinthProjectId,
        "githubRepository" to githubRepository,
        "hikariVersion" to hikariVersion,
        "sqliteVersion" to sqliteVersion,
        "mariadbVersion" to mariadbVersion,
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "build-info.properties")) {
        expand(props)
    }
}

// Die fertige Plugin-JAR (MRadmin-mc26.3-v1.2.0.jar) inkl. Socket.IO-Client. Die Pakete werden umbenannt, damit sie sich
// nicht mit anderen Plugins beißen, die z.B. auch OkHttp oder org.json mitbringen.
tasks.shadowJar {
    archiveClassifier.set("")
    val lib = "at.marcelr.mradmin.lib"
    relocate("io.socket", "$lib.socketio")
    relocate("okhttp3", "$lib.okhttp3")
    relocate("okio", "$lib.okio")
    relocate("org.json", "$lib.json")
    exclude("META-INF/maven/**", "META-INF/*.kotlin_module")
    mergeServiceFiles()
}

// Die JAR ohne Bibliotheken bekommt den Zusatz "-plain", damit sie nicht mit der Plugin-JAR verwechselt wird.
tasks.jar {
    archiveClassifier.set("plain")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

// Veröffentlichung auf Modrinth: ./gradlew modrinth (braucht die Umgebungsvariable MODRINTH_TOKEN).
// Normalerweise erledigt das der GitHub-Workflow .github/workflows/release.yml beim Pushen eines Tags.
modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN"))
    projectId.set(modrinthProjectId)
    versionNumber.set(project.version.toString())
    versionName.set("MRadmin $pluginVersion (MC $mcVersion)")
    // 0.x und Versionen mit Suffix (1.0.0-rc.1) sind Vorabversionen -> auf Modrinth als "beta".
    versionType.set(if (isPrerelease) "beta" else "release")
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(supportedGameVersions.split(",").map { it.trim() })
    loaders.addAll("paper", "purpur")
    changelog.set(providers.environmentVariable("CHANGELOG").orElse("Siehe CHANGELOG.md"))
}

// ------------------------------------------------------------------ Weboberfläche
// Der Svelte-Code in web/ wird mit Vite nach build/web-dist gebaut und als web/ in die Plugin-JAR gepackt.
node {
    download.set(true)
    version.set("24.19.0")
    nodeProjectDir.set(file("web"))
    // "npm ci" installiert genau die Versionen aus package-lock.json.
    npmInstallCommand.set("ci")
}

val buildWeb = tasks.register<com.github.gradle.node.npm.task.NpmTask>("buildWeb") {
    description = "Baut die Weboberfläche (web/) nach build/web-dist."
    dependsOn(tasks.named("npmInstall"))
    args.set(listOf("run", "build"))
    inputs.dir("web/src")
    inputs.dir("web/public")
    inputs.files("web/index.html", "web/package.json", "web/package-lock.json", "web/vite.config.js")
    outputs.dir(layout.buildDirectory.dir("web-dist"))
}

tasks.processResources {
    dependsOn(buildWeb)
    from(layout.buildDirectory.dir("web-dist")) {
        into("web")
    }
}
