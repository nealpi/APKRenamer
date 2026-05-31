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

    // ARSCLib 的 ID_xxx 是 Java static int，不是 Kotlin 编译期常量，用 val
    private val ATTR_NAME = AndroidManifestBlock.ID_name
    private val ATTR_LABEL = AndroidManifestBlock.ID_label
    private val ATTR_AUTHORITIES = AndroidManifestBlock.ID_authorities
    private val ATTR_TARGET_ACTIVITY = AndroidManifestBlock.ID_targetActivity

    private val COMPONENT_TAGS = listOf("activity", "service", "receiver", "provider")

    fun rewrite(
        manifest: AndroidManifestBlock,
        oldPackage: String,
        newPackage: String,
        newDisplayName: String,
        authoritySuffix: String,
    ) {
        // A. 顶层 package
        manifest.packageName = newPackage

        // B. 找 <application>
        val application: ResXmlElement = manifest.applicationElement
            ?: error("manifest 中未找到 <application> 节点")

        // application 自己的 android:name（自定义 Application 类）
        expandAttrIfRelative(application, ATTR_NAME, "android:name", oldPackage)

        // D. label
        setStringAttribute(application, ATTR_LABEL, "label", newDisplayName)

        // C. 按标签名分别处理组件
        for (tag in COMPONENT_TAGS) {
            for (child in application.listElements(tag)) {
                expandAttrIfRelative(child, ATTR_NAME, "android:name", oldPackage)
                if (tag == "provider") {
                    rewriteAuthority(child, authoritySuffix)
                }
            }
        }

        // activity-alias 单独处理（多一个 targetActivity 属性）
        for (alias in application.listElements("activity-alias")) {
            expandAttrIfRelative(alias, ATTR_NAME, "android:name", oldPackage)
            expandAttrIfRelative(alias, ATTR_TARGET_ACTIVITY, "android:targetActivity", oldPackage)
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
