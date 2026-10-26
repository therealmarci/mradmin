// MRadmin-API: die Schnittstelle, über die andere Plugins (MRchunks, MRsleep, MRpvp, MRtoolbar ...) optional mit
// MRadmin zusammenarbeiten. Plugins binden sie nur zum Kompilieren ein (compileOnly) - zur Laufzeit kommen die Klassen
// aus der MRadmin-JAR. Veröffentlicht über JitPack (Tag "api-<version>") bzw. lokal mit publishToMavenLocal.
plugins {
    `java-library`
    `maven-publish`
}

val paperApi = providers.gradleProperty("paperApi").get()
val apiVersion = providers.gradleProperty("apiVersion").get()

// Koordinaten wie bei JitPack: com.github.therealmarci:mradmin:<tag> (JitPack setzt GROUP/VERSION selbst).
group = System.getenv("GROUP") ?: "com.github.therealmarci"
version = System.getenv("VERSION") ?: "api-$apiVersion"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:none", "-quiet")
    }
}

publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "mradmin"
            from(components["java"])
        }
    }
}
