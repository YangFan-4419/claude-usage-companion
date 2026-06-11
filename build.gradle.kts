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

fun Project.deviceProperty(serial: String, propertyName: String): String {
    val output = ByteArrayOutputStream()
    exec {
        commandLine(adbPath(), "-s", serial, "shell", "getprop", propertyName)
        standardOutput = output
        isIgnoreExitValue = true
    }
    return output.toString().trim()
}

fun Project.looksLikeWearDevice(serial: String, deviceLine: String): Boolean {
    return deviceLine.contains("gwear", ignoreCase = true) ||
        deviceLine.contains("wear", ignoreCase = true) ||
        deviceLine.contains("watch", ignoreCase = true) ||
        deviceProperty(serial, "ro.product.characteristics").contains("watch", ignoreCase = true) ||
        deviceProperty(serial, "ro.build.characteristics").contains("watch", ignoreCase = true)
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
        .map { line -> line.substringBefore(' ').trim() to line }
        .filter { (serial, line) ->
            val isEmulator = serial.startsWith("emulator-")
            val looksLikeWear = looksLikeWearDevice(serial, line)
            when (targetKind) {
                "wear" -> looksLikeWear
                "phone" -> !looksLikeWear
                "realWear" -> !isEmulator && looksLikeWear
                "realPhone" -> !isEmulator && !looksLikeWear
                else -> false
            }
        }
        .map { (serial, _) -> serial }
        .filter { it.isNotBlank() }
        .toList()

    return when (matches.size) {
        1 -> listOf("-s", matches.single())
        0 -> throw GradleException("No $targetKind target found. Connect the right device or pass -P$propertyName=<serial>.")
        else -> throw GradleException("Multiple $targetKind targets found: ${matches.joinToString()}. Pass -P$propertyName=<serial>.")
    }
}

fun Project.adbExec(serialProperty: String, targetKind: String, vararg args: String) {
    exec {
        commandLine(listOf(adbPath()) + serialArgs(serialProperty, targetKind) + args)
    }
}

tasks.register("runPhoneDebug") {
    group = "device"
    description = "Install and launch the phone app on an emulator or device. Use -PphoneSerial=<serial> or ANDROID_SERIAL when multiple targets are connected."
    dependsOn(":app:assembleDebug")

    doLast {
        val apk = rootProject.file("app/build/outputs/apk/debug/app-debug.apk").absolutePath
        adbExec("phoneSerial", "phone", "install", "-r", apk)
        adbExec("phoneSerial", "phone", "shell", "am", "start", "-n", "com.usagecompanion.claude/.MainActivity")
    }
}

tasks.register("runWearDebug") {
    group = "device"
    description = "Install and launch the Wear OS app on an emulator or device. Use -PwearSerial=<serial> or ANDROID_SERIAL when multiple targets are connected."
    dependsOn(":wear:assembleDebug")

    doLast {
        val apk = rootProject.file("wear/build/outputs/apk/debug/wear-debug.apk").absolutePath
        adbExec("wearSerial", "wear", "install", "-r", apk)
        adbExec("wearSerial", "wear", "shell", "am", "start", "-n", "com.usagecompanion.claude/com.usagecompanion.claude.wear.WearMainActivity")
    }
}

tasks.register("runPhoneDeviceDebug") {
    group = "device"
    description = "Install and launch the phone app on a physical Android device. Use -PphoneSerial=<serial> when auto-detection is ambiguous."
    dependsOn(":app:assembleDebug")

    doLast {
        val apk = rootProject.file("app/build/outputs/apk/debug/app-debug.apk").absolutePath
        adbExec("phoneSerial", "realPhone", "install", "-r", apk)
        adbExec("phoneSerial", "realPhone", "shell", "am", "start", "-n", "com.usagecompanion.claude/.MainActivity")
    }
}

tasks.register("runWearDeviceDebug") {
    group = "device"
    description = "Install and launch the Wear OS app on a physical Wear device. Use -PwearSerial=<serial> when auto-detection is ambiguous."
    dependsOn(":wear:assembleDebug")

    doLast {
        val apk = rootProject.file("wear/build/outputs/apk/debug/wear-debug.apk").absolutePath
        adbExec("wearSerial", "realWear", "install", "-r", apk)
        adbExec("wearSerial", "realWear", "shell", "am", "start", "-n", "com.usagecompanion.claude/com.usagecompanion.claude.wear.WearMainActivity")
    }
}
