// Settings shared by every Java subproject, kept here so the modules cannot drift apart.

plugins {
    id("com.diffplug.spotless") version "8.11.0" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    group = "dev.ahmedmohsin.blackbox"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        // A toolchain pins the compiler to Java 21 whatever JDK happens to launch Gradle,
        // so a laptop and the CI runner compile with the same version.
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    // `./gradlew build` runs spotlessCheck, so badly formatted code fails the build
    // locally and in CI. `./gradlew spotlessApply` fixes it.
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat()
            removeUnusedImports()
        }
    }
}
