plugins {
    id("com.android.application") version "8.11.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

import java.util.Properties
import java.io.ByteArrayOutputStream

fun androidSdkDir(): File {
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) {
        val properties = Properties()
        localProperties.inputStream().use(properties::load)
        properties.getProperty("sdk.dir")?.let { return file(it) }
    }

    System.getenv("ANDROID_HOME")?.let { return file(it) }
    System.getenv("ANDROID_SDK_ROOT")?.let { return file(it) }
    return file("${System.getProperty("user.home")}/Library/Android/sdk")
}

fun adbPath(): String {
    val executable = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
    return androidSdkDir().resolve("platform-tools/$executable").absolutePath
}

fun Project.serialArgs(propertyName: String, targetKind: String): List<String> {
    val serial = providers.gradleProperty(propertyName)
        .orElse(providers.environmentVariable("ANDROID_SERIAL"))
        .orNull
        ?.takeIf { it.isNotBlank() }

    if (serial != null) return listOf("-s", serial)

    val output = ByteArrayOutputStream()
    exec {
        commandLine(adbPath(), "devices", "-l")
        standardOutput = output
    }

    val matches = output.toString()
        .lineSequence()
        .map { it.trim() }
        .filter { it.contains(Regex("\\sdevice\\s")) }
        .filter {
            when (targetKind) {
                "wear" -> it.contains("gwear", ignoreCase = true) || it.contains("wear", ignoreCase = true)
                "phone" -> !it.contains("gwear", ignoreCase = true) && !it.contains("wear", ignoreCase = true)
                else -> false
            }
        }
        .map { it.substringBefore(' ').trim() }
        .filter { it.isNotBlank() }
        .toList()

    return when (matches.size) {
        1 -> listOf("-s", matches.single())
        0 -> throw GradleException("No $targetKind emulator/device found. Start the right emulator or pass -P$propertyName=<serial>.")
        else -> throw GradleException("Multiple $targetKind devices found: ${matches.joinToString()}. Pass -P$propertyName=<serial>.")
    }
}

fun Project.adbExec(serialProperty: String, targetKind: String, vararg args: String) {
    exec {
        commandLine(listOf(adbPath()) + serialArgs(serialProperty, targetKind) + args)
    }
}

tasks.register("runPhoneDebug") {
    group = "emulator"
    description = "Install and launch the phone app. Use -PphoneSerial=emulator-5554 or ANDROID_SERIAL when multiple devices are connected."
    dependsOn(":app:assembleDebug")

    doLast {
        val apk = rootProject.file("app/build/outputs/apk/debug/app-debug.apk").absolutePath
        adbExec("phoneSerial", "phone", "install", "-r", apk)
        adbExec("phoneSerial", "phone", "shell", "am", "start", "-n", "com.usagecompanion.claude/.MainActivity")
    }
}

tasks.register("runWearDebug") {
    group = "emulator"
    description = "Install and launch the Wear OS app. Use -PwearSerial=emulator-5556 or ANDROID_SERIAL when multiple devices are connected."
    dependsOn(":wear:assembleDebug")

    doLast {
        val apk = rootProject.file("wear/build/outputs/apk/debug/wear-debug.apk").absolutePath
        adbExec("wearSerial", "wear", "install", "-r", apk)
        adbExec("wearSerial", "wear", "shell", "am", "start", "-n", "com.usagecompanion.claude/com.usagecompanion.claude.wear.WearMainActivity")
    }
}
