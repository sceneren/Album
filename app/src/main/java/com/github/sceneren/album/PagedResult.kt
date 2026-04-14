package com.github.sceneren.album

/**
 * 分页结果数据类
 *
 * 对查询结果进行分页封装，提供当前页数据及完整的分页导航信息。
 *
 * @param T              数据元素的类型
 * @property data            当前页的数据列表
 * @property page            当前页码（从 1 开始）
 * @property pageSize        每页数据条数
 * @property totalCount      符合查询条件的数据总条数
 * @property totalPages      总页数
 * @property hasNextPage     是否存在下一页
 * @property hasPreviousPage 是否存在上一页
 */
data class PagedResult<T>(
    val data: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean
)