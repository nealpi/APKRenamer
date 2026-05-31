// 顶层 build.gradle.kts —— 只声明插件，不应用
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
