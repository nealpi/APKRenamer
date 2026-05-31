package com.yitian.apkrenamer.core

import android.util.Log
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.chunk.xml.ResXmlElement

/**
 * AndroidManifest 改写器。
 *
 * 三件事，按重要性排序：
 *  A. 改 manifest 根节点的 package 属性 —— 不改就装不上"两个版本"，因为 Android 用它定身份
 *  B. 把所有组件的 android:name 从 ".MainActivity" 这种相对名展开成 "com.x.MainActivity" 绝对名
 *     —— 不改的话，新 package 装上后 Android 会去找 newpkg.MainActivity，类不存在，闪退
 *  C. 改写所有 <provider> 的 authorities，加上 .clone_xxxx 后缀
 *     —— ContentProvider 的 authority 在系统里是全局唯一的，两个 APK 装同一 authority 会拒装
 *
 * 顺带：
 *  D. 改 <application android:label>，让桌面上能区分两个图标
 */
object ManifestRewriter {

    private const val TAG = "ManifestRewriter"

    // Android 命名空间下的几个关键属性 resourceId（写死，避免每次反查）
    private const val ATTR_NAME = AndroidManifestBlock.ID_name              // android:name
    private const val ATTR_LABEL = AndroidManifestBlock.ID_label            // android:label
    private const val ATTR_AUTHORITIES = AndroidManifestBlock.ID_authorities

    fun rewrite(
        manifest: AndroidManifestBlock,
        oldPackage: String,
        newPackage: String,
        newDisplayName: String,
        authoritySuffix: String,
    ) {
        // ---- A. 顶层 package ----
        manifest.packageName = newPackage

        val root: ResXmlElement = manifest.manifestElement
            ?: error("manifest 根节点丢失")

        // ---- B. 展开 application/activity/service/receiver/provider 的 android:name ----
        val application = root.getElementByTagName("application")
        if (application != null) {
            // application 自己的 android:name（自定义 Application 类）也要展开
            expandComponentName(application, oldPackage)

            // D. 改 label —— 把字符串直接写进去，覆盖原资源引用
            //    用户改的是显示名，不是字符串资源，所以直接 setString 最简单
            setStringAttribute(application, ATTR_LABEL, "android:label", newDisplayName)

            for (child in application.listElements()) {
                when (child.name) {
                    "activity",
                    "activity-alias",
                    "service",
                    "receiver",
                    "provider" -> {
                        expandComponentName(child, oldPackage)
                        if (child.name == "provider") {
                            rewriteAuthority(child, authoritySuffix)
                        }
                    }
                }
            }

            // activity-alias 还可能有 android:targetActivity，也得展开
            for (child in application.listElements("activity-alias")) {
                expandAttrIfRelative(
                    child,
                    AndroidManifestBlock.ID_targetActivity,
                    "android:targetActivity",
                    oldPackage,
                )
            }
        }
    }

    /** 把组件的 android:name 展开成绝对类名（如果是 .Xxx 或 Xxx 这种相对写法） */
    private fun expandComponentName(element: ResXmlElement, oldPackage: String) {
        expandAttrIfRelative(element, ATTR_NAME, "android:name", oldPackage)
    }

    private fun expandAttrIfRelative(
        element: ResXmlElement,
        attrResId: Int,
        attrPrettyName: String,
        oldPackage: String,
    ) {
        val attr: ResXmlAttribute = element.searchAttributeByResourceId(attrResId) ?: return
        val current = attr.valueAsString ?: return
        if (current.isBlank()) return

        val absolute = when {
            current.startsWith(".") -> oldPackage + current               // ".Foo" -> "com.x.Foo"
            !current.contains(".") -> "$oldPackage.$current"              // "Foo"  -> "com.x.Foo"
            else -> current                                                // 已是绝对名
        }
        if (absolute != current) {
            attr.setValueAsString(absolute)
            Log.d(TAG, "<${element.name}> $attrPrettyName: $current -> $absolute")
        }
    }

    /**
     * 改写 <provider android:authorities="a;b;c"/>
     * Android 允许分号分隔多个 authority。每个都追加 .后缀。
     */
    private fun rewriteAuthority(providerElement: ResXmlElement, suffix: String) {
        if (suffix.isBlank()) return
        val attr = providerElement.searchAttributeByResourceId(ATTR_AUTHORITIES) ?: return
        val current = attr.valueAsString ?: return
        val rewritten = current.split(';')
            .filter { it.isNotBlank() }
            .joinToString(";") { "$it.clone_$suffix" }
        attr.setValueAsString(rewritten)
        Log.d(TAG, "<provider> authorities: $current -> $rewritten")
    }

    /**
     * 把某个属性强行设为字符串字面值（即使原本是资源引用 @string/xxx）。
     * 如果属性不存在则创建。
     */
    private fun setStringAttribute(
        element: ResXmlElement,
        attrResId: Int,
        prettyName: String,
        value: String,
    ) {
        var attr = element.searchAttributeByResourceId(attrResId)
        if (attr == null) {
            attr = element.createAndroidAttribute(prettyName.removePrefix("android:"), attrResId)
        }
        attr.setValueAsString(value)
    }
}
