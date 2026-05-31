package com.yitian.apkrenamer.core

import android.util.Log
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.chunk.xml.ResXmlElement

/**
 * AndroidManifest 改写器（已适配 ARSCLib V1.3.8 API）。
 */
object ManifestRewriter {

    private const val TAG = "ManifestRewriter"

    // ARSCLib 的 ID_xxx 是普通 static int，不是 Kotlin 编译期常量，所以用 val 而不是 const val
    private val ATTR_NAME = AndroidManifestBlock.ID_name
    private val ATTR_LABEL = AndroidManifestBlock.ID_label
    private val ATTR_AUTHORITIES = AndroidManifestBlock.ID_authorities
    private val ATTR_TARGET_ACTIVITY = AndroidManifestBlock.ID_targetActivity

    fun rewrite(
        manifest: AndroidManifestBlock,
        oldPackage: String,
        newPackage: String,
        newDisplayName: String,
        authoritySuffix: String,
    ) {
        // ---- A. 顶层 package ----
        manifest.packageName = newPackage

        // ---- B. 找 <application> —— 用 ARSCLib 提供的便捷方法，绕开 getElementByTagName ----
        val application: ResXmlElement = manifest.applicationElement
            ?: error("manifest 中未找到 <application> 节点")

        // application 自身的 android:name（自定义 Application 类）
        expandAttrIfRelative(application, ATTR_NAME, "android:name", oldPackage)

        // D. label
        setStringAttribute(application, ATTR_LABEL, "label", newDisplayName)

        // ---- C. 遍历 application 的子节点，处理组件 ----
        for (child in application.listElements()) {
            when (child.name) {
                "activity", "service", "receiver", "provider" -> {
                    expandAttrIfRelative(child, ATTR_NAME, "android:name", oldPackage)
                    if (child.name == "provider") {
                        rewriteAuthority(child, authoritySuffix)
                    }
                }
                "activity-alias" -> {
                    expandAttrIfRelative(child, ATTR_NAME, "android:name", oldPackage)
                    expandAttrIfRelative(child, ATTR_TARGET_ACTIVITY, "android:targetActivity", oldPackage)
                }
            }
        }
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
            current.startsWith(".") -> oldPackage + current
            !current.contains(".") -> "$oldPackage.$current"
            else -> current
        }
        if (absolute != current) {
            attr.setValueAsString(absolute)
            Log.d(TAG, "<${element.name}> $attrPrettyName: $current -> $absolute")
        }
    }

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

    private fun setStringAttribute(
        element: ResXmlElement,
        attrResId: Int,
        prettyName: String,
        value: String,
    ) {
        var attr = element.searchAttributeByResourceId(attrResId)
        if (attr == null) {
            attr = element.createAndroidAttribute(prettyName, attrResId)
        }
        attr.setValueAsString(value)
    }
}
