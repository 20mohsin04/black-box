plugins {
    application
}

dependencies {
    // Jackson is used only to parse and write JSON. See docs/decisions/0001.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.23.0")
}

application {
    mainClass.set("dev.ahmedmohsin.blackbox.demo.ScriptedClient")
}

// `./gradlew :demo:scenario` plays the scripted agent against the sample server.
// The sandbox lives under build/ so it is throwaway and never committed.
tasks.register<JavaExec>("scenario") {
    group = "demo"
    description = "Runs the scripted client against the sample tool server in a fresh sandbox."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.ahmedmohsin.blackbox.demo.ScriptedClient")

    val sandbox = layout.buildDirectory.dir("sandbox")
    doFirst {
        val dir = sandbox.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        args("--sandbox", dir.absolutePath)
    }
}
