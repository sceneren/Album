package com.github.sceneren.album.api.internal.database

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 把持久授权整理纳入分页加载，使整理异常能够通过 Paging 重试。
 *
 * @param prepare 创建 Room 分页源前执行的整理操作，不读取图片内容。
 * @param createSource 整理完成后创建 Room 分页源，避免整理删除记录导致新源提前失效。
 */
internal class PreparedPickedMediaPagingSource(
    private val prepare: suspend () -> Unit,
    private val createSource: () -> PagingSource<Int, PickedMediaEntity>,
) : PagingSource<Int, PickedMediaEntity>() {
    private val mutex = Mutex()
    @Volatile
    private var source: PagingSource<Int, PickedMediaEntity>? = null

    init {
        registerInvalidatedCallback { source?.invalidate() }
    }

    /**
     * 首次加载前整理授权，后续加载交给 Room；失败保留为可重试状态。
     * @param params Paging 请求的键及数量。
     * @return 数据页、失效结果或可重试错误；协程取消继续向上传播。
     */
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PickedMediaEntity> = try {
        val delegate = mutex.withLock {
            source ?: run {
                prepare()
                createSource().also { created ->
                    source = created
                    created.registerInvalidatedCallback { invalidate() }
                    if (invalid) {
                        created.invalidate()
                    }
                }
            }
        }
        delegate.load(params)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exception: Exception) {
        LoadResult.Error(exception)
    }

    /**
     * 复用 Room 的刷新位置计算。
     * @param state 当前分页快照及锚点。
     * @return Room 刷新键；尚未初始化时从首页开始。
     */
    override fun getRefreshKey(state: PagingState<Int, PickedMediaEntity>): Int? =
        source?.getRefreshKey(state)
}
