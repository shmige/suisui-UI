// Top-level build file (2026-09 构建链升级: 参考 KernelSU-Style-UI-Kit 同款, 与 G:\qing 一致)
// AGP 9 内置 Kotlin 支持, 不再需要 org.jetbrains.kotlin.android 插件
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
