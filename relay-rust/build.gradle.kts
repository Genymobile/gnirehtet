import org.gradle.api.tasks.Exec

tasks.register<Exec>("debug") {
    commandLine("cargo", "build")
}

tasks.register<Exec>("release") {
    commandLine("cargo", "build", "--release")
}

tasks.register<Exec>("test") {
    commandLine("cargo", "test")
}

tasks.register<Exec>("install") {
    commandLine("cargo", "install")
}

tasks.register<Exec>("cleanRust") {
    commandLine("cargo", "clean")
}

tasks.register<Exec>("checkstyle") {
    commandLine("cargo", "fmt", "--", "--check")
}

tasks.register("check") {
    dependsOn("checkstyle", "test")
}

tasks.register("buildRust") {
    dependsOn("check", "debug", "release")
}

tasks.register<Exec>("releaseCrossToWindows") {
    commandLine("cargo", "build", "--release", "--target=x86_64-pc-windows-gnu")
}
