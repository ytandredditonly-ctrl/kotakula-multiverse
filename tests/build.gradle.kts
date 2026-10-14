import com.unciv.build.BuildConfig
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

// Java 21+ deprecates dynamic agent loading: https://openjdk.org/jeps/451
val mockitoAgent = configurations.create("mockitoAgent")

// Support Gradle 9 caching seeing the mockito agent
private class MockitoAgentArgumentProvider(
    @get:Classpath val agentJar: FileCollection
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-javaagent:${agentJar.singleFile}")
}

dependencies {
    testImplementation(libs.junit)
    @Suppress("AvoidDuplicateDependencies") // false positive
    testImplementation(libs.mockito)
    @Suppress("AvoidDuplicateDependencies")
    mockitoAgent(libs.mockito) { isTransitive = false }
}

tasks {
    test {
        workingDir = file("../android/assets")
        testLogging.lifecycle {
            events(
                    TestLogEvent.FAILED,
                    TestLogEvent.STANDARD_ERROR,
                    TestLogEvent.STANDARD_OUT
            )

            exceptionFormat = TestExceptionFormat.FULL
        }

        jvmArgumentProviders.add(MockitoAgentArgumentProvider(mockitoAgent))

        // Forward latency-test save file path to the test JVM
        System.getProperty("unciv.nextTurnSaveFile")?.let { systemProperty("unciv.nextTurnSaveFile", it) }
    }

    // Turn speed for the Objective Judge Horizon benchmark. A developer tool,
    // never part of `check`: it plays hundreds of turns and prints a machine
    // protocol, which is not a test and would not belong in one.
    //
    //   ./gradlew :tests:ojhTurns -Pojh.args="--turns 250 --players 63 --map /path/map.json"
    // Kotakula Multiverse: generates the mod ruleset from docs/kotakula/spec.md (see com.unciv.dev.kotakula)
    register<JavaExec>("kotakulaMod") {
        group = "kotakula"
        description = "Generate the Kotakula Multiverse ruleset into android/assets/KotakulaMod"
        mainClass.set("com.unciv.dev.kotakula.KotakulaModGenerator")
        classpath = sourceSets["test"].runtimeClasspath
        workingDir = file("../android/assets")
    }

    register<JavaExec>("kotakulaIcons") {
        group = "kotakula"
        description = "Draw the P1 icons of Kotakula Multiverse into its Images folder"
        mainClass.set("com.unciv.dev.kotakula.KotakulaIconPainter")
        classpath = sourceSets["test"].runtimeClasspath
        workingDir = file("../android/assets")
        jvmArgs("-Djava.awt.headless=true")
    }

    register<JavaExec>("kotakulaSvgIcons") {
        group = "kotakula"
        description = "Render the SVG icons of art/kotakula-icons into the Images folder of Kotakula Multiverse"
        mainClass.set("com.unciv.dev.kotakula.KotakulaSvgIcons")
        classpath = sourceSets["test"].runtimeClasspath
        workingDir = file("../android/assets")
        jvmArgs("-Djava.awt.headless=true")
    }

    register<JavaExec>("kotakulaTiles") {
        group = "kotakula"
        description = "Draw the pixel-art tiles of the three-layer map (Kotakula tileset) into the Images folder of Kotakula Multiverse"
        mainClass.set("com.unciv.dev.kotakula.KotakulaTilePainter")
        classpath = sourceSets["test"].runtimeClasspath
        workingDir = file("../android/assets")
        jvmArgs("-Djava.awt.headless=true")
    }

    register<JavaExec>("kotakulaAtlas") {
        group = "kotakula"
        description = "Pack the Images of Kotakula Multiverse into game.atlas and write the install index (run after kotakulaIcons)"
        mainClass.set("com.unciv.dev.kotakula.KotakulaAtlasPacker")
        classpath = sourceSets["test"].runtimeClasspath
        workingDir = file("../android/assets")
        jvmArgs("-Djava.awt.headless=true")
        mustRunAfter("kotakulaMod", "kotakulaIcons", "kotakulaSvgIcons", "kotakulaTiles")
    }

    register<JavaExec>("kotakulaAutoplay") {
        group = "kotakula"
        description = "AI-only games on Kotakula Multiverse with the metrics of spec section 14.3"
        mainClass.set("com.unciv.dev.kotakula.KotakulaAutoplay")
        classpath = sourceSets["test"].runtimeClasspath
        workingDir = file("../android/assets")
        // -Pkotakula.args="..." or the KOTAKULA_ARGS environment variable (gradlew.bat mangles quoted arguments)
        args = ((project.findProperty("kotakula.args") as String?) ?: System.getenv("KOTAKULA_ARGS") ?: "")
            .split(" ").filter { it.isNotEmpty() }
    }

    register<JavaExec>("ojh") {
        group = "verification"
        description = "Measure turn speed or frame rate for OJH (see com.unciv.dev.OjhBenchmark)"
        mainClass.set("com.unciv.dev.OjhBenchmark")
        classpath = sourceSets["test"].runtimeClasspath
        workingDir = file("../android/assets")
        args = ((project.findProperty("ojh.args") as String?) ?: "").split(" ").filter { it.isNotEmpty() }
        // The fps mode opens a real window, and GLFW on macOS insists on being
        // on the first thread of the process; headless AWT keeps AWT from
        // taking that same thread over (see OjhBenchmark.fps). Both are
        // harmless for the turns mode, which opens no window at all.
        if (System.getProperty("os.name").startsWith("Mac"))
            jvmArgs("-XstartOnFirstThread", "-Djava.awt.headless=true")
    }
}

sourceSets {
    test {
        java.srcDir("src")
    }
}

eclipse.project {
    name = "${BuildConfig.appName}-tests"
}

// Print the tests runtime classpath, so the OJH benchmark can be launched as a
// plain `java` process. Its fps mode opens a window and times frames, and a
// window owned by a Gradle worker does not get one.
tasks.register("ojhClasspath") {
    val cp = sourceSets["test"].runtimeClasspath
    doLast { println(cp.asPath) }
}
