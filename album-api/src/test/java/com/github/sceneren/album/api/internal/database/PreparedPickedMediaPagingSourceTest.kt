package com.github.sceneren.album.api.internal.database

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.sceneren.album.api.internal.database.PickedMediaEntity
import com.github.sceneren.album.api.internal.database.PreparedPickedMediaPagingSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证授权整理异常、取消和 Room 失效在分页边界中正确传播。 */
class PreparedPickedMediaPagingSourceTest {
    @Test
    /**
     * 整理失败时不创建 Room 源，同一源重试成功后不重复整理。
     * @return 无返回值，断言失败可恢复且初始化只成功执行一次。
     */
    fun preparationFailureCanRetry() = runTest {
        var attempts = 0
        var creations = 0
        val source = PreparedPickedMediaPagingSource(
            prepare = {
                attempts++
                if (attempts == 1) {
                    throw SecurityException("模拟授权查询失败")
                }
            },
            createSource = {
                creations++
                EmptySource()
            },
        )

        assertTrue(source.load(refresh()) is PagingSource.LoadResult.Error)
        assertEquals(0, creations)
        assertTrue(source.load(refresh()) is PagingSource.LoadResult.Page)
        assertTrue(source.load(refresh()) is PagingSource.LoadResult.Page)
        assertEquals(2, attempts)
        assertEquals(1, creations)
    }

    @Test
    /**
     * 验证双向失效，确保数据库变更能够刷新 UI，取消旧代时也停止其 Room 源。
     * @return 无返回值，断言包装源和 Room 源的失效标记。
     */
    fun invalidationPropagatesBothWays() = runTest {
        val room = EmptySource()
        val source = PreparedPickedMediaPagingSource(
            prepare = {},
            createSource = { room },
        )
        source.load(refresh())
        assertFalse(source.invalid)
        room.invalidate()
        assertTrue(source.invalid)

        val nextRoom = EmptySource()
        val nextSource = PreparedPickedMediaPagingSource(
            prepare = {},
            createSource = { nextRoom },
        )
        nextSource.load(refresh())
        nextSource.invalidate()
        assertTrue(nextRoom.invalid)
    }

    @Test
    /**
     * 页面退出导致取消时，不能伪装成可重试错误。
     * @return 无返回值，断言取消异常继续传播。
     */
    fun cancellationIsNotConvertedToLoadError() = runTest {
        val source = PreparedPickedMediaPagingSource(
            prepare = { throw CancellationException("页面已关闭") },
            createSource = { EmptySource() },
        )
        var cancelled = false
        try {
            source.load(refresh())
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    /**
     * 创建统一的首页加载参数。
     * @return 无占位符的首页请求。
     */
    private fun refresh(): PagingSource.LoadParams.Refresh<Int> = PagingSource.LoadParams.Refresh(
        key = null,
        loadSize = 20,
        placeholdersEnabled = false,
    )

    /** 提供无内容但支持失效通知的 Room 替身。 */
    private class EmptySource : PagingSource<Int, PickedMediaEntity>() {
        /**
         * 返回已到末尾的空页。
         * @param params 加载请求。
         * @return 空数据页。
         */
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PickedMediaEntity> =
            LoadResult.Page(
                data = emptyList(),
                prevKey = null,
                nextKey = null,
            )

        /**
         * 空页始终从首页刷新。
         * @param state 当前分页状态。
         * @return null 表示首页。
         */
        override fun getRefreshKey(state: PagingState<Int, PickedMediaEntity>): Int? = null
    }
}
