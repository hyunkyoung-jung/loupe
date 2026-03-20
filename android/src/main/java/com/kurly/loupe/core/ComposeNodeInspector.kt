package com.kurly.loupe.core

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.kurly.loupe.token.ColorTokenRegistry
import com.kurly.loupe.util.DimensionUtil

/**
 * Compose 내부 레이아웃 트리를 탐색하여 디자인 정보를 추출하는 인스펙터.
 *
 * 두 가지 접근을 병합:
 * 1. LayoutInfo 트리 — 모든 Compose 노드의 bounds/modifier 정보 (reflection 필요)
 * 2. SemanticsNode 트리 — Text, Button 등 시맨틱 정보 보강
 *
 * DEBUG 빌드에서만 사용됩니다.
 */
object ComposeNodeInspector {

    private const val TAG = "ComposeInspector"

    fun findComposeView(root: View): View? {
        if (isAndroidComposeView(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findComposeView(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    fun isAndroidComposeView(view: View): Boolean {
        var clazz: Class<*>? = view.javaClass
        while (clazz != null) {
            if (clazz.name == "androidx.compose.ui.platform.AndroidComposeView") return true
            clazz = clazz.superclass
        }
        return false
    }

    /**
     * Compose 뷰에서 터치 좌표에 해당하는 노드의 DesignInfo를 추출한다.
     */
    fun inspectAt(composeView: View, screenX: Int, screenY: Int): DesignInfo? {
        val viewLocation = IntArray(2)
        composeView.getLocationOnScreen(viewLocation)
        val localX = (screenX - viewLocation[0]).toFloat()
        val localY = (screenY - viewLocation[1]).toFloat()
        val context = composeView.context

        // 1) LayoutInfo 트리 (모든 Compose 노드를 잡아냄)
        val layoutInfo = inspectViaLayoutInfo(composeView, localX, localY, viewLocation, context)

        // 2) SemanticsNode 트리 (Text/Button 등 시맨틱 정보 보강)
        val semanticsInfo = inspectViaSemanticsNode(composeView, localX, localY, viewLocation, context)

        if (layoutInfo == null && semanticsInfo == null) return null

        return mergeInfo(layoutInfo, semanticsInfo)
    }

    // ═══════════════════════════════════════
    // 방법 1: LayoutInfo 트리 (public interface)
    // ═══════════════════════════════════════

    private fun inspectViaLayoutInfo(
        composeView: View,
        localX: Float,
        localY: Float,
        viewLocation: IntArray,
        context: Context,
    ): DesignInfo? {
        val rootLayoutInfo = getRootLayoutInfo(composeView) ?: return null
        val target = findDeepestLayoutInfo(rootLayoutInfo, localX, localY) ?: return null
        return extractFromLayoutInfo(context, target, viewLocation)
    }

    /**
     * AndroidComposeView → root (LayoutNode, implements LayoutInfo)
     */
    private fun getRootLayoutInfo(composeView: View): LayoutInfo? {
        return try {
            val field = findField(composeView.javaClass, "root")
            if (field != null) {
                field.isAccessible = true
                field.get(composeView) as? LayoutInfo
            } else {
                Log.d(TAG, "root field not found in ${composeView.javaClass.name}")
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "getRootLayoutInfo failed: ${e.message}")
            null
        }
    }

    /**
     * LayoutInfo 트리에서 좌표에 해당하는 가장 깊은 노드를 찾는다.
     */
    private fun findDeepestLayoutInfo(node: LayoutInfo, x: Float, y: Float): LayoutInfo? {
        if (!node.isAttached || !node.isPlaced) return null

        // 자식 노드 가져오기
        val children = getChildLayoutInfos(node)

        // 자식부터 역순 탐색 (z-order)
        for (i in children.indices.reversed()) {
            val found = findDeepestLayoutInfo(children[i], x, y)
            if (found != null) return found
        }

        // 현재 노드 바운드 체크
        val coords = node.coordinates
        if (coords.isAttached) {
            val pos = coords.positionInRoot()
            val size = coords.size
            if (size.width > 0 && size.height > 0 &&
                x >= pos.x && x <= pos.x + size.width &&
                y >= pos.y && y <= pos.y + size.height
            ) {
                return node
            }
        }

        return null
    }

    /**
     * LayoutInfo의 자식 노드를 가져온다.
     * childrenInfo (public API, Compose 1.5+) → _children field (reflection fallback)
     */
    @Suppress("UNCHECKED_CAST")
    private fun getChildLayoutInfos(node: LayoutInfo): List<LayoutInfo> {
        // 1) Public API: childrenInfo (Compose 1.5+)
        try {
            val method = node.javaClass.getMethod("getChildrenInfo")
            val result = method.invoke(node) as? List<LayoutInfo>
            if (result != null) return result
        } catch (_: Exception) {
        }

        // 2) Reflection: _children (MutableVector<LayoutNode>)
        try {
            val field = findField(node.javaClass, "_children") ?: return emptyList()
            field.isAccessible = true
            val value = field.get(node) ?: return emptyList()

            // List인 경우
            if (value is List<*>) {
                return value.filterIsInstance<LayoutInfo>()
            }

            // MutableVector인 경우 — asMutableList() 시도
            try {
                val asMutableList = value.javaClass.getMethod("asMutableList")
                val list = asMutableList.invoke(value) as? List<*>
                if (list != null) return list.filterIsInstance<LayoutInfo>()
            } catch (_: Exception) {
            }

            // MutableVector — size + get(index) 시도
            try {
                val sizeMethod = value.javaClass.getMethod("getSize")
                val getMethod = value.javaClass.getMethod("get", Int::class.java)
                val size = sizeMethod.invoke(value) as Int
                return (0 until size).mapNotNull {
                    try {
                        getMethod.invoke(value, it) as? LayoutInfo
                    } catch (_: Exception) {
                        null
                    }
                }
            } catch (_: Exception) {
            }

            // content 필드로 배열 직접 접근
            try {
                val contentField = findField(value.javaClass, "content")
                contentField?.isAccessible = true
                val arr = contentField?.get(value) as? Array<*>
                val sizeField = findField(value.javaClass, "size")
                sizeField?.isAccessible = true
                val size = (sizeField?.get(value) as? Int) ?: 0
                return (0 until size).mapNotNull { arr?.get(it) as? LayoutInfo }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }

        return emptyList()
    }

    private fun extractFromLayoutInfo(
        context: Context,
        node: LayoutInfo,
        viewLocation: IntArray,
    ): DesignInfo {
        val coords = node.coordinates
        val pos = coords.positionInRoot()
        val size = coords.size
        val density = context.resources.displayMetrics.density

        val screenLeft = viewLocation[0] + pos.x.toInt()
        val screenTop = viewLocation[1] + pos.y.toInt()

        // modifier 정보 추출 시도
        val modDetails = extractModifierDetails(node)

        return DesignInfo(
            viewClassName = classifyNode(modDetails),
            viewId = null,
            bounds = Rect(screenLeft, screenTop, screenLeft + size.width, screenTop + size.height),
            backgroundColor = modDetails.backgroundColor,
            textColor = null,
            tintColor = null,
            paddingLeft = modDetails.paddingLeft,
            paddingTop = modDetails.paddingTop,
            paddingRight = modDetails.paddingRight,
            paddingBottom = modDetails.paddingBottom,
            marginLeft = 0,
            marginTop = 0,
            marginRight = 0,
            marginBottom = 0,
            textSizeSp = null,
            fontWeight = null,
            letterSpacingSp = null,
            lineHeightSp = null,
            widthDp = (size.width / density).toInt(),
            heightDp = (size.height / density).toInt(),
            alpha = 1f,
            elevation = 0f,
            cornerRadiusDp = modDetails.cornerRadius,
            isComposeNode = true,
            composeModifiers = modDetails.modifierNames,
        )
    }

    /**
     * LayoutInfo.getModifierInfo()로 modifier 정보 추출.
     * isDebugInspectorInfoEnabled = true 일 때 padding, background 등의 상세 정보를 얻을 수 있다.
     */
    private fun extractModifierDetails(node: LayoutInfo): ModifierDetails {
        var paddingLeft = 0
        var paddingTop = 0
        var paddingRight = 0
        var paddingBottom = 0
        var bgColor: ColorInfo? = null
        var cornerRadius: Float? = null
        val modifierNames = mutableListOf<String>()

        try {
            val modInfoList = node.getModifierInfo()

            for (modInfo in modInfoList) {
                val modifier = modInfo.modifier

                // InspectorInfo에서 name, properties 추출
                val name = getModifierName(modifier) ?: continue
                modifierNames.add(name)

                val props = getModifierProperties(modifier)

                when (name) {
                    "padding" -> parsePaddingProps(props).let { (l, t, r, b) ->
                        if (l > 0) paddingLeft = l
                        if (t > 0) paddingTop = t
                        if (r > 0) paddingRight = r
                        if (b > 0) paddingBottom = b
                    }
                    "background" -> {
                        val color = props["color"]
                        if (color != null) {
                            getColorValue(color)?.let { bgColor = ColorTokenRegistry.resolve(it) }
                        }
                    }
                    "clip" -> {
                        val shape = props["shape"]
                        if (shape != null) {
                            cornerRadius = extractCornerRadius(shape)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "extractModifierDetails failed: ${e.message}")
        }

        return ModifierDetails(
            paddingLeft = paddingLeft,
            paddingTop = paddingTop,
            paddingRight = paddingRight,
            paddingBottom = paddingBottom,
            backgroundColor = bgColor,
            cornerRadius = cornerRadius,
            modifierNames = modifierNames,
        )
    }

    private fun getModifierName(modifier: Any): String? {
        // InspectorInfo.getName()
        try {
            val method = modifier.javaClass.getMethod("getName")
            val name = method.invoke(modifier) as? String
            if (!name.isNullOrEmpty()) return name
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * InspectorInfo에서 properties를 추출한다.
     *
     * Compose InspectorInfo는 두 곳에 값을 저장:
     * - getValue(): 단일 값 modifier (e.g., padding(16.dp) → value=Dp(16))
     * - getProperties(): 다중 값 modifier (e.g., padding(start=8.dp) → properties={start=Dp(8)})
     *
     * ValueElement는 name/value 프로퍼티를 가진다.
     */
    private fun getModifierProperties(modifier: Any): Map<String, Any?> {
        val props = mutableMapOf<String, Any?>()

        // 1) getValue() — 단일 값 (e.g., padding(16.dp))
        try {
            val method = modifier.javaClass.getMethod("getValue")
            val value = method.invoke(modifier)
            if (value != null) props["value"] = value
        } catch (_: Exception) {
        }

        // 2) getProperties() — Sequence<ValueElement>
        try {
            val method = modifier.javaClass.getMethod("getProperties")
            val seq = method.invoke(modifier) as? Sequence<*> ?: return props
            seq.forEach { element ->
                if (element == null) return@forEach
                val propName = try {
                    element.javaClass.getMethod("getName").invoke(element) as? String
                } catch (_: Exception) {
                    null
                }
                val propValue = try {
                    element.javaClass.getMethod("getValue").invoke(element)
                } catch (_: Exception) {
                    null
                }
                if (propName != null) props[propName] = propValue
            }
        } catch (_: Exception) {
        }

        return props
    }

    // ═══════════════════════════════════════
    // 방법 2: SemanticsNode 트리
    // ═══════════════════════════════════════

    private fun inspectViaSemanticsNode(
        composeView: View,
        localX: Float,
        localY: Float,
        viewLocation: IntArray,
        context: Context,
    ): DesignInfo? {
        val semanticsOwner = getSemanticsOwner(composeView) ?: return null
        val rootNode = getUnmergedRootNode(semanticsOwner) ?: return null
        val target = findDeepestSemanticsNode(rootNode, localX, localY) ?: return null
        return extractFromSemanticsNode(context, target, viewLocation)
    }

    private fun getSemanticsOwner(composeView: View): Any? {
        // getSemanticsOwner() method
        try {
            val method = composeView.javaClass.getMethod("getSemanticsOwner")
            return method.invoke(composeView)
        } catch (_: Exception) {
        }
        // field fallback
        try {
            val field = findField(composeView.javaClass, "semanticsOwner")
            field?.isAccessible = true
            return field?.get(composeView)
        } catch (_: Exception) {
        }
        return null
    }

    private fun getUnmergedRootNode(semanticsOwner: Any): SemanticsNode? {
        return try {
            val method = semanticsOwner.javaClass.getMethod("getUnmergedRootSemanticsNode")
            method.invoke(semanticsOwner) as? SemanticsNode
        } catch (_: Exception) {
            null
        }
    }

    /**
     * SemanticsNode 트리에서 좌표에 해당하는 가장 깊은 노드를 찾는다.
     * hasUsefulSemantics 필터 없이, bounds에 들어오는 가장 깊은 노드를 반환한다.
     */
    private fun findDeepestSemanticsNode(
        node: SemanticsNode,
        x: Float,
        y: Float,
    ): SemanticsNode? {
        // 자식부터 역순 탐색
        for (child in node.children.reversed()) {
            val found = findDeepestSemanticsNode(child, x, y)
            if (found != null) return found
        }

        // 현재 노드 바운드 체크 (루트 노드는 전체 화면이므로 자식이 없을 때만)
        val bounds = node.boundsInRoot
        if (bounds.width > 0f && bounds.height > 0f &&
            x >= bounds.left && x <= bounds.right &&
            y >= bounds.top && y <= bounds.bottom
        ) {
            // 루트 노드(id == -1 등)가 아닌 실제 콘텐츠 노드만 반환
            if (node.config.any()) {
                return node
            }
        }

        return null
    }

    private fun extractFromSemanticsNode(
        context: Context,
        node: SemanticsNode,
        viewLocation: IntArray,
    ): DesignInfo {
        val bounds = node.boundsInRoot
        val density = context.resources.displayMetrics.density
        val config = node.config

        val screenLeft = viewLocation[0] + bounds.left.toInt()
        val screenTop = viewLocation[1] + bounds.top.toInt()
        val width = bounds.width.toInt()
        val height = bounds.height.toInt()

        // 텍스트 추출
        val textList = config.getOrNull(SemanticsProperties.Text)
        val text = textList?.firstOrNull()
        val editableText = config.getOrNull(SemanticsProperties.EditableText)

        // 커스텀 디자인 토큰 속성
        val bgColorInt = config.getOrNull(DesignBackgroundColorKey)
        val bgColor = bgColorInt?.let { ColorTokenRegistry.resolve(it) }

        val textColorInt = config.getOrNull(DesignTextColorKey)
        val textColor = textColorInt?.let { ColorTokenRegistry.resolve(it) }

        val paddingStr = config.getOrNull(DesignPaddingKey)
        val paddings = parsePadding(paddingStr)

        val testTag = config.getOrNull(SemanticsProperties.TestTag)
        val role = config.getOrNull(SemanticsProperties.Role)
        val contentDesc = config.getOrNull(SemanticsProperties.ContentDescription)
        val viewId = testTag ?: role?.toString()

        // 텍스트 속성 추출
        val textSizeSp = text?.spanStyles?.firstOrNull()?.let {
            val fontSize = it.item.fontSize
            if (fontSize.isSp) fontSize.value else null
        }
        val fontWeight = text?.spanStyles?.firstOrNull()?.item?.fontWeight?.weight?.toString()

        // 클래스명 추정
        val className = when {
            textList != null -> "Text"
            editableText != null -> "TextField"
            role?.toString() == "Button" -> "Button"
            role?.toString() == "Image" -> "Image"
            role?.toString() == "Checkbox" -> "Checkbox"
            role?.toString() == "Switch" -> "Switch"
            role?.toString() == "Tab" -> "Tab"
            contentDesc != null -> "Image"
            else -> "Compose"
        }

        return DesignInfo(
            viewClassName = className,
            viewId = viewId,
            bounds = Rect(screenLeft, screenTop, screenLeft + width, screenTop + height),
            backgroundColor = bgColor,
            textColor = textColor,
            tintColor = null,
            paddingLeft = paddings[0],
            paddingTop = paddings[1],
            paddingRight = paddings[2],
            paddingBottom = paddings[3],
            marginLeft = 0,
            marginTop = 0,
            marginRight = 0,
            marginBottom = 0,
            textSizeSp = textSizeSp,
            fontWeight = fontWeight,
            letterSpacingSp = null,
            lineHeightSp = null,
            widthDp = (width / density).toInt(),
            heightDp = (height / density).toInt(),
            alpha = 1f,
            elevation = 0f,
            cornerRadiusDp = null,
            isComposeNode = true,
            composeModifiers = emptyList(),
        )
    }

    // ═══════════════════════════════════════
    // 정보 병합
    // ═══════════════════════════════════════

    private fun mergeInfo(layout: DesignInfo?, semantics: DesignInfo?): DesignInfo {
        if (layout == null && semantics != null) return semantics
        if (layout != null && semantics == null) return layout

        val l = layout!!
        val s = semantics!!

        // semantics bounds가 더 구체적(작은)이면 그것을 사용
        val useSemBounds = s.bounds.width() in 1 until l.bounds.width() ||
            s.bounds.height() in 1 until l.bounds.height()

        return l.copy(
            viewClassName = if (s.viewClassName != "Compose") s.viewClassName else l.viewClassName,
            viewId = s.viewId ?: l.viewId,
            bounds = if (useSemBounds) s.bounds else l.bounds,
            backgroundColor = l.backgroundColor ?: s.backgroundColor,
            textColor = s.textColor ?: l.textColor,
            textSizeSp = s.textSizeSp ?: l.textSizeSp,
            fontWeight = s.fontWeight ?: l.fontWeight,
            paddingLeft = if (l.paddingLeft > 0) l.paddingLeft else s.paddingLeft,
            paddingTop = if (l.paddingTop > 0) l.paddingTop else s.paddingTop,
            paddingRight = if (l.paddingRight > 0) l.paddingRight else s.paddingRight,
            paddingBottom = if (l.paddingBottom > 0) l.paddingBottom else s.paddingBottom,
        )
    }

    // ═══════════════════════════════════════
    // 유틸리티
    // ═══════════════════════════════════════

    private data class ModifierDetails(
        val paddingLeft: Int = 0,
        val paddingTop: Int = 0,
        val paddingRight: Int = 0,
        val paddingBottom: Int = 0,
        val backgroundColor: ColorInfo? = null,
        val cornerRadius: Float? = null,
        val modifierNames: List<String> = emptyList(),
    )

    private fun classifyNode(modDetails: ModifierDetails): String {
        val mods = modDetails.modifierNames
        return when {
            "clickable" in mods && "clip" in mods -> "Button"
            "clickable" in mods -> "Clickable"
            "verticalScroll" in mods || "horizontalScroll" in mods -> "Scrollable"
            "background" in mods && "clip" in mods -> "Surface"
            mods.isNotEmpty() -> "Compose(${mods.take(3).joinToString(",")})"
            else -> "Compose"
        }
    }

    private fun findField(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun getFieldValue(obj: Any, name: String): Any? {
        return try {
            val field = findField(obj.javaClass, name) ?: return null
            field.isAccessible = true
            field.get(obj)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Compose Color (inline class, ULong 기반) → ARGB Int 변환
     */
    private fun getColorValue(color: Any): Int? {
        // 방법 1: toArgb-impl static method (Compose Color inline class)
        try {
            val toArgb = color.javaClass.getMethod("toArgb-impl", Long::class.java)
            val valueField = findField(color.javaClass, "value")
            valueField?.isAccessible = true
            val value = valueField?.getLong(color) ?: return null
            return toArgb.invoke(null, value) as? Int
        } catch (_: Exception) {
        }

        // 방법 2: value 필드에서 직접 추출
        try {
            val valueField = findField(color.javaClass, "value")
            valueField?.isAccessible = true
            val value = valueField?.get(color)
            return (value as? Number)?.toInt()
        } catch (_: Exception) {
        }

        return null
    }

    private fun extractCornerRadius(shape: Any): Float? {
        return try {
            val topStart = shape.javaClass.getMethod("getTopStart")
            val cornerSize = topStart.invoke(shape) ?: return null
            val str = cornerSize.toString()
            Regex("(\\d+\\.?\\d*)").find(str)?.value?.toFloatOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePaddingProps(props: Map<String, Any?>): List<Int> {
        // 단일 값: padding(16.dp) → InspectorInfo.value = Dp(16)
        val singleValue = dpToInt(props["value"])
        if (singleValue > 0) return listOf(singleValue, singleValue, singleValue, singleValue)

        // padding(all = 16.dp)
        val all = dpToInt(props["all"])
        if (all > 0) return listOf(all, all, all, all)

        // padding(horizontal = 8.dp, vertical = 16.dp)
        val h = dpToInt(props["horizontal"])
        val v = dpToInt(props["vertical"])
        if (h > 0 || v > 0) return listOf(h, v, h, v)

        // padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)
        return listOf(
            dpToInt(props["start"]),
            dpToInt(props["top"]),
            dpToInt(props["end"]),
            dpToInt(props["bottom"]),
        )
    }

    /**
     * Dp value 추출.
     *
     * Dp는 inline class(Float 기반)로, InspectorInfo에서:
     * - Float으로 unbox 되어 전달될 수 있음
     * - Dp 객체로 box 되어 전달될 수 있음
     * - toString() 시 "16.0.dp" 형태
     */
    private fun dpToInt(value: Any?): Int {
        if (value == null) return 0

        // Float/Number로 직접 전달된 경우
        if (value is Float) return value.toInt()
        if (value is Number) return value.toFloat().toInt()

        // Dp inline class → backing field "value" (Float)
        try {
            val field = findField(value.javaClass, "value")
            if (field != null) {
                field.isAccessible = true
                return field.getFloat(value).toInt()
            }
        } catch (_: Exception) {
        }

        // toString fallback: "16.0.dp" → 16
        try {
            val str = value.toString()
            return Regex("([\\d.]+)").find(str)?.value?.toFloatOrNull()?.toInt() ?: 0
        } catch (_: Exception) {
        }

        return 0
    }

    private fun parsePadding(paddingStr: String?): IntArray {
        if (paddingStr == null) return intArrayOf(0, 0, 0, 0)
        val parts = paddingStr.split(",").map { it.trim().toIntOrNull() ?: 0 }
        return when (parts.size) {
            4 -> intArrayOf(parts[0], parts[1], parts[2], parts[3])
            2 -> intArrayOf(parts[0], parts[1], parts[0], parts[1])
            1 -> intArrayOf(parts[0], parts[0], parts[0], parts[0])
            else -> intArrayOf(0, 0, 0, 0)
        }
    }

    /**
     * SemanticsConfiguration이 비어있지 않은지 확인.
     * iterator로 최소 하나의 속성이 있는지 체크.
     */
    private fun Iterable<*>.any(): Boolean {
        return iterator().hasNext()
    }
}