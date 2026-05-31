package com.yitian.apkrenamer.core

import android.util.Log
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.chunk.xml.ResXmlElement

object ManifestRewriter {

    private const val TAG = "ManifestRewriter"

    private val ATTR_NAME = AndroidManifestBlock.ID_name
    private val ATTR_LABEL = AndroidManifestBlock.ID_label
    private val ATTR_AUTHORITIES = AndroidManifestBlock.ID_authorities
    private val ATTR_TARGET_ACTIVITY = AndroidManifestBlock.ID_targetActivity

    private val COMPONENT_TAGS = listOf("activity", "service", "receiver", "provider")
    private val PERMISSION_TAGS = listOf("permission", "permission-tree", "permission-group")

    fun rewrite(
        manifest: AndroidManifestBlock,
        oldPackage: String,
        newPackage: String,
        newDisplayName: String,
        authoritySuffix: String,
    ) {
        // A. 顶层 package
        manifest.packageName = newPackage

        // 新增：拿到 manifest 根节点（用于改 <permission> 等）
        val manifestRoot: ResXmlElement = manifest.manifestElement
            ?: error("manifest 根节点丢失")

        // ---- 关键修复：处理自定义 <permission>、<permission-tree>、<permission-group> ----
        // 不改名会跟原 APK 冲突，整包装不上
        for (tag in PERMISSION_TAGS) {
            for (p in manifestRoot.listElements(tag)) {
                val attr = p.searchAttributeByResourceId(ATTR_NAME) ?: continue
                val name = attr.valueAsString ?: continue
                if (name.isNotBlank()) {
                    val renamed = "$name.clone_$authoritySuffix"
                    attr.setValueAsString(renamed)
                    Log.d(TAG, "<$tag> android:name: $name -> $renamed")
                }
            }
        }

        // 同步改写 <uses-permission>：如果引用的是原 APK 自定义的权限，也要跟着改
        for (up in manifestRoot.listElements("uses-permission")) {
            val attr = up.searchAttributeByResourceId(ATTR_NAME) ?: continue
            val name = attr.valueAsString ?: continue
            // 系统权限（android.permission.xxx）不动，只动以原包名开头的自定义权限
            if (name.startsWith("$oldPackage.")) {
                val renamed = "$name.clone_$authoritySuffix"
                attr.setValueAsString(renamed)
                Log.d(TAG, "<uses-permission> $name -> $renamed")
            }
        }

        // B. <application>
        val application: ResXmlElement = manifest.applicationElement
            ?: error("manifest 中未找到 <application> 节点")

        expandAttrIfRelative(application, ATTR_NAME, "android:name", oldPackage)
        setStringAttribute(application, ATTR_LABEL, "label", newDisplayName)

        // C. 组件
        for (tag in COMPONENT_TAGS) {
            for (child in application.listElements(tag)) {
                expandAttrIfRelative(child, ATTR_NAME, "android:name", oldPackage)
                if (tag == "provider") {
                    rewriteAuthority(child, authoritySuffix)
                }
            }
        }

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
