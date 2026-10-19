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
val webauthnVersion = providers.gradleProperty("webauthnVersion").get()
val jacksonVersion = providers.gradleProperty("jacksonVersion").get()
val mariadbVersion = providers.gradleProperty("mariadbVersion").get()
val libertyBansVersion = providers.gradleProperty("libertyBansVersion").get()
val omnibusVersion = providers.gradleProperty("omnibusVersion").get()
val supportedGameVersions = providers.gradleProperty("supportedGameVersions").get()
val isPrerelease = pluginVersion.let { it.startsWith("0.") || it.contains("-") }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // LibertyBans-API (optional, nur zum Kompilieren) und Omnibus, über das LibertyBans seine API bereitstellt.
    maven("https://mvn-repo.arim.space/affero-gpl3/")
    maven("https://mvn-repo.arim.space/lesser-gpl3/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    // Optional: Rechte schon vor dem Join prüfen (Wartungsmodus). Ohne LuckPerms wird erst beim Join gekickt.
    compileOnly("net.luckperms:api:5.5")
    // Optional: Banns, Mutes, Warns und Kicks über LibertyBans auf der Spielerseite im Web. Ohne LibertyBans fehlt der Teil.
    compileOnly("space.arim.libertybans:bans-api:$libertyBansVersion")
    compileOnly("space.arim.omnibus:omnibus:$omnibusVersion")
    // Uptime-Kuma-Anbindung (Socket.IO). Steckt mit in der JAR (umbenannt nach at.marcelr.mradmin.lib, siehe shadowJar).
    implementation("io.socket:socket.io-client:$socketIoVersion")
    // Datenbank: nur zum Kompilieren - auf dem Server lädt Paper sie über "libraries" in der plugin.yml.
    compileOnly("com.zaxxer:HikariCP:$hikariVersion")
    // Passkeys (WebAuthn, Yubico) - steckt mit in der JAR (umbenannt, siehe shadowJar). Nicht über "libraries":
    // dessen Versionsbereiche (z.B. Jackson [2.13,3)) ließe Paper beim Start hunderte Dateien laden.
    implementation("com.yubico:webauthn-server-core:$webauthnVersion") {
        exclude(group = "org.slf4j") // bringt Paper mit
    }
    // Feste, stabile Jackson-Version statt des Bereichs aus Yubicos POM (der sonst ein SNAPSHOT erwischen kann).
    implementation(enforcedPlatform("com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
    implementation("com.fasterxml.jackson.core:jackson-core") // Version aus der BOM; nötig zum Kompilieren
    // Auch hier feste, stabile Versionen statt der Bereiche (httpclient5 [5,6) träfe sonst eine Alpha).
    constraints {
        implementation("org.apache.httpcomponents.client5:httpclient5") { version { strictly("5.6.4") } }
        implementation("com.google.guava:guava") { version { strictly("33.7.2-jre") } }
        implementation("com.upokecenter:cbor") { version { strictly("4.5.6") } }
    }

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
    relocate("com.yubico", "$lib.yubico")
    relocate("com.fasterxml.jackson", "$lib.jackson")
    relocate("com.google.common", "$lib.guava")
    relocate("com.google.thirdparty", "$lib.guava.thirdparty")
    relocate("com.google.errorprone", "$lib.errorprone")
    relocate("com.google.j2objc", "$lib.j2objc")
    relocate("org.checkerframework", "$lib.checkerframework")
    relocate("org.apache.hc", "$lib.httpclient")
    relocate("co.nstant", "$lib.cbor")
    relocate("upokecenter", "$lib.upokecenter")
    relocate("com.upokecenter", "$lib.upokecenter")
    relocate("org.jspecify", "$lib.jspecify")
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
