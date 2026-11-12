package com.github.sceneren.album.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.sceneren.album.api.AlbumMedia

/**
 * 嵌入式选择器与宿主标题栏、确认栏之间的状态桥接。
 * 一个实例只能绑定一个 [AlbumPickerContent]；离开组合后操作不再生效。
 * 选择会话由组件保存，宿主无需自行保存 URI 或申请媒体授权。
 */
@Stable
class AlbumPickerState internal constructor() {
    /** 当前所选媒体，顺序与最终结果一致。 */
    var selectedItems: List<AlbumMedia> by mutableStateOf(emptyList())
        private set

    /** 正在处理 Photo Picker 返回及其列表刷新、生成结果或取消会话时为 true。 */
    var isProcessing: Boolean by mutableStateOf(false)
        private set

    /** 是否可以提交当前选择。 */
    val canConfirm: Boolean get() = isAttached && selectedItems.isNotEmpty() && !isProcessing

    private var owner: Any? = null
    private var actions: Actions? = null
    private var isAttached by mutableStateOf(false)

    /** 复制、压缩所选媒体，成功后调用组件的 onResult。必须在主线程调用。 */
    fun confirm() { if (canConfirm) actions?.confirm?.invoke() }

    /** 在组件区域内预览已选媒体。 */
    fun preview() { if (!isProcessing) actions?.preview?.invoke() }

    /** 关闭预览；若未预览，则取消会话并调用 onCancel。 */
    fun cancel() { if (!isProcessing) actions?.cancel?.invoke() }

    /** 检查权限及媒体变化；仅在内容变化时更新列表，保留当前滚动位置。 */
    fun refresh() { if (!isProcessing) actions?.refresh?.invoke() }

    internal fun attach(token: Any) {
        check(owner == null || owner === token) { "AlbumPickerState cannot be shared by active pickers" }
        owner = token
        isAttached = true
    }

    internal fun update(token: Any, selected: List<AlbumMedia>, processing: Boolean, callbacks: Actions) {
        if (owner !== token) return
        selectedItems = selected.toList()
        isProcessing = processing
        actions = callbacks
    }

    internal fun detach(token: Any) {
        if (owner !== token) return
        owner = null
        isAttached = false
        actions = null
        selectedItems = emptyList()
        isProcessing = false
    }

    internal class Actions(
        val confirm: () -> Unit,
        val preview: () -> Unit,
        val cancel: () -> Unit,
        val refresh: () -> Unit,
    )
}

/** 在宿主组合中创建供 [AlbumPickerContent] 使用的状态。 */
@Composable
fun rememberAlbumPickerState(): AlbumPickerState = remember { AlbumPickerState() }
