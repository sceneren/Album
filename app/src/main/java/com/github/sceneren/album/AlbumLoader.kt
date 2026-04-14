package com.github.sceneren.album

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片查询工具类
 *
 * 基于 MediaStore API 提供对设备图片的查询能力，全部操作使用协程在 IO 线程执行，
 * 不会阻塞主线程。返回的图片地址均为 content:// URI，兼容 Android 各版本
 * （包括 Android 10+ 的分区存储），可直接用于 Glide / Coil 等主流图片加载框架。
 *
 * 使用前必须调用 init(context) 进行初始化，建议在 Application.onCreate() 中调用。
 *
 * 功能概述：
 * - getImageDirectories：获取设备中所有包含图片的目录（相册）列表，首项为"全部图片"
 * - getAllImages：分页获取设备中的全部图片
 * - getImagesByDirectory：根据目录 ID 分页获取该目录下的图片，
 *   当传入 ImageDirectory.ALL_BUCKET_ID 时等价于 getAllImages
 *
 * 分页兼容策略：
 * - Android 11 (API 30) 及以上：使用官方推荐的 Bundle 参数
 * - Android 11 以下（API 24-29）：在 sortOrder 子句中追加 LIMIT / OFFSET
 *
 * 注意事项：
 * - 调用方需自行处理存储权限
 * - 分页页码从 1 开始
 * - 查询结果默认按图片添加时间倒序排列（最新的在前）
 * - pageSize 参数如不传则使用 DEFAULT_PAGE_SIZE（50）
 */
object AlbumLoader {

    /**
     * 默认每页数量
     *
     * 当调用方未指定 pageSize 时使用此值，
     * 50 条是在内存占用与用户体验之间的平衡点。
     */
    const val DEFAULT_PAGE_SIZE = 50

    // 存储应用级别的 Context，使用 applicationContext 避免内存泄漏
    private lateinit var appContext: Context

    // MediaStore 图片表的 content:// 基础 URI，对应外部存储中的全部图片资源
    private val IMAGES_URI: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    // 查询图片详情时需要的列投影，包含图片的全部元数据字段
    private val IMAGE_PROJECTION = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )

    // 查询目录信息时需要的列投影（轻量级），仅包含构建目录摘要所需的最少字段
    private val DIRECTORY_PROJECTION = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )

    // 默认排序规则：按图片添加时间倒序排列，确保最新的图片排在前面
    private const val DEFAULT_SORT_ORDER = MediaStore.Images.Media.DATE_ADDED + " DESC"

    /**
     * 目录信息聚合器（内部辅助类）
     *
     * 在遍历全部图片的 Cursor 过程中，临时存储每个目录的统计信息。
     * 将可变的聚合逻辑限制在此内部类中，
     * 对外暴露的 ImageDirectory 保持不可变，符合数据类的设计原则。
     *
     * @property bucketId   目录唯一标识
     * @property bucketName 目录显示名称
     * @property coverUri   封面图片的 URI（该目录中最新的一张图片）
     * @property count      该目录下的图片计数，在遍历过程中递增
     */
    private class MutableDirectoryInfo(
        val bucketId: Long,
        val bucketName: String,
        val coverUri: Uri,
        var count: Int = 0
    )

    /**
     * 初始化 AlbumLoader
     *
     * 必须在使用任何查询方法之前调用，建议在 Application.onCreate() 中调用。
     * 内部会自动提取 applicationContext，因此传入 Activity 或 Service 的 context 也是安全的，
     * 不会造成内存泄漏。
     *
     * 重复调用是安全的，后续调用会覆盖之前的 context。
     *
     * @param context 任意 Context 实例（Activity、Service、Application 均可）
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 获取 ContentResolver 实例
     *
     * 从初始化时存储的 applicationContext 中获取 ContentResolver。
     * 若未调用 init() 则抛出异常，提供明确的错误提示。
     *
     * @return ContentResolver 实例
     * @throws IllegalStateException 当 AlbumLoader 未初始化时抛出
     */
    private fun requireContentResolver(): ContentResolver {
        check(::appContext.isInitialized) {
            "AlbumLoader is not initialized. Call AlbumLoader.init(context) first."
        }
        return appContext.contentResolver
    }

    /**
     * 获取设备中所有包含图片的目录列表
     *
     * 遍历 MediaStore 中的全部图片记录，按目录（Bucket）进行分组统计。
     * 由于查询按时间倒序排列，每个目录首次出现时对应的图片即为最新图片，
     * 自动作为该目录的封面。
     *
     * 返回列表的第一项固定为"全部图片"虚拟目录（bucketId = ImageDirectory.ALL_BUCKET_ID），
     * 其 imageCount 为设备上所有图片的总数，coverUri 为全局最新的一张图片。
     * 后续各项为真实目录，按图片数量降序排列。
     *
     * 该方法在 IO 调度器上执行，调用方无需额外切换线程。
     *
     * @return 图片目录列表，首项为"全部图片"，后续按图片数量从多到少排列；
     *         若设备上无图片则返回空列表
     * @throws IllegalStateException 当 AlbumLoader 未初始化时抛出
     */
    suspend fun getImageDirectories(): List<ImageDirectory> {
        return withContext(Dispatchers.IO) {
            val contentResolver = requireContentResolver()

            // 使用 LinkedHashMap 保持插入顺序，key 为 bucketId
            val directoryMap = LinkedHashMap<Long, MutableDirectoryInfo>()

            // 记录全局最新图片的 URI，用于"全部图片"目录的封面
            var firstImageUri: Uri? = null
            // 记录全部图片的总数
            var totalImageCount = 0

            val cursor = contentResolver.query(
                IMAGES_URI,
                DIRECTORY_PROJECTION,
                null,
                null,
                DEFAULT_SORT_ORDER
            )

            cursor?.use { c ->
                // 预先获取列索引，避免在循环中重复查找
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (c.moveToNext()) {
                    val imageId = c.getLong(idCol)
                    val bucketId = c.getLong(bucketIdCol)
                    val bucketName = c.getString(bucketNameCol).orEmpty()

                    totalImageCount++

                    // 第一行就是全局最新的图片（查询按 DATE_ADDED DESC 排序）
                    if (firstImageUri == null) {
                        firstImageUri = ContentUris.withAppendedId(IMAGES_URI, imageId)
                    }

                    // 首次遇到某个目录时记录第一张图片作为封面
                    // 因为查询按 DATE_ADDED DESC 排序，第一次遇到的就是该目录中最新的图片
                    val info = directoryMap.getOrPut(bucketId) {
                        MutableDirectoryInfo(
                            bucketId = bucketId,
                            bucketName = bucketName,
                            coverUri = ContentUris.withAppendedId(IMAGES_URI, imageId)
                        )
                    }
                    // 累加该目录下的图片计数
                    info.count++
                }
            }

            // 设备上没有图片时直接返回空列表
            if (totalImageCount == 0 || firstImageUri == null) {
                return@withContext emptyList()
            }

            // 将真实目录转换为不可变对象并按图片数量降序排列
            val realDirectories = directoryMap.values
                .map { info ->
                    ImageDirectory(
                        bucketId = info.bucketId,
                        bucketName = info.bucketName,
                        coverUri = info.coverUri,
                        imageCount = info.count
                    )
                }
                .sortedByDescending { it.imageCount }

            // 构建"全部图片"虚拟目录，放在列表首位
            val allDirectory = ImageDirectory(
                bucketId = ImageDirectory.ALL_BUCKET_ID,
                bucketName = ImageDirectory.ALL_BUCKET_NAME,
                coverUri = firstImageUri,
                imageCount = totalImageCount
            )

            // 将"全部图片"插入到列表头部
            val result = ArrayList<ImageDirectory>(realDirectories.size + 1)
            result.add(allDirectory)
            result.addAll(realDirectories)
            result
        }
    }

    /**
     * 分页获取设备中的全部图片
     *
     * 查询 MediaStore 中外部存储的所有图片，支持自定义每页数量。
     * 返回的 PagedResult 包含当前页数据和完整的分页导航信息，
     * 调用方可通过 PagedResult.hasNextPage 判断是否需要加载更多。
     *
     * 该方法在 IO 调度器上执行，调用方无需额外切换线程。
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页数量，默认为 DEFAULT_PAGE_SIZE（50），必须大于 0
     * @return 分页结果，包含当前页图片列表及分页信息
     * @throws IllegalArgumentException 当 page 小于 1 或 pageSize 小于 1 时抛出
     * @throws IllegalStateException 当 AlbumLoader 未初始化时抛出
     */
    suspend fun getAllImages(
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PagedResult<ImageItem> {
        require(page >= 1) { "page must be >= 1, got: $page" }
        require(pageSize >= 1) { "pageSize must be >= 1, got: $pageSize" }

        return withContext(Dispatchers.IO) {
            val contentResolver = requireContentResolver()

            // 先查询满足条件的图片总数，用于计算分页信息
            val totalCount = queryTotalCount(contentResolver, null, null)

            // 查询当前页的图片数据
            val images = queryImagesWithPaging(contentResolver, null, null, page, pageSize)

            buildPagedResult(images, page, pageSize, totalCount)
        }
    }

    /**
     * 根据目录 ID 分页获取该目录下的图片
     *
     * 通过 BUCKET_ID 过滤，仅查询指定目录中的图片。
     * bucketId 可从 getImageDirectories 返回的 ImageDirectory.bucketId 获取。
     *
     * 当传入 ImageDirectory.ALL_BUCKET_ID 时，等价于调用 getAllImages，
     * 查询设备上的所有图片，不做目录过滤。
     *
     * 该方法在 IO 调度器上执行，调用方无需额外切换线程。
     *
     * @param bucketId 目录 ID，可通过 getImageDirectories 获取；
     *                 传入 ImageDirectory.ALL_BUCKET_ID 表示查询全部图片
     * @param page     页码，从 1 开始
     * @param pageSize 每页数量，默认为 DEFAULT_PAGE_SIZE（50），必须大于 0
     * @return 分页结果，包含指定目录下当前页图片列表及分页信息
     * @throws IllegalArgumentException 当 page 小于 1 或 pageSize 小于 1 时抛出
     * @throws IllegalStateException 当 AlbumLoader 未初始化时抛出
     */
    suspend fun getImagesByDirectory(
        bucketId: Long,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PagedResult<ImageItem> {
        // 当传入"全部图片"的哨兵 ID 时，直接委托给 getAllImages
        if (bucketId == ImageDirectory.ALL_BUCKET_ID) {
            return getAllImages(page, pageSize)
        }

        require(page >= 1) { "page must be >= 1, got: $page" }
        require(pageSize >= 1) { "pageSize must be >= 1, got: $pageSize" }

        return withContext(Dispatchers.IO) {
            val contentResolver = requireContentResolver()

            // 构建 WHERE 子句：按 BUCKET_ID 过滤
            val selection = MediaStore.Images.Media.BUCKET_ID + " = ?"
            val selectionArgs = arrayOf(bucketId.toString())

            // 查询指定目录下的图片总数
            val totalCount = queryTotalCount(contentResolver, selection, selectionArgs)

            // 查询当前页的图片数据
            val images = queryImagesWithPaging(contentResolver, selection, selectionArgs, page, pageSize)

            buildPagedResult(images, page, pageSize, totalCount)
        }
    }

    /**
     * 查询符合条件的图片总数
     *
     * 仅投影 _ID 列并通过 Cursor.count 获取总行数，
     * 避免加载完整的图片元数据，内存开销极小。
     *
     * @param contentResolver 内容解析器
     * @param selection       WHERE 子句，传 null 表示无过滤条件（查询全部）
     * @param selectionArgs   WHERE 子句的参数值数组
     * @return 满足条件的图片总数，查询失败时返回 0
     */
    private fun queryTotalCount(
        contentResolver: ContentResolver,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val countProjection = arrayOf(MediaStore.Images.Media._ID)

        // Android 11+ 使用 Bundle 传递查询参数，低版本使用传统 selection 参数
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle()
            if (selection != null) {
                queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }
            val cursor = contentResolver.query(IMAGES_URI, countProjection, queryArgs, null)
            return cursor?.use { it.count } ?: 0
        } else {
            val cursor = contentResolver.query(IMAGES_URI, countProjection, selection, selectionArgs, null)
            return cursor?.use { it.count } ?: 0
        }
    }

    /**
     * 带分页的图片查询（核心查询方法）
     *
     * 根据 Android 系统版本选择最佳的分页策略：
     * - Android 11 (API 30) 及以上：使用官方推荐的 Bundle 参数，
     *   通过 QUERY_ARG_LIMIT 和 QUERY_ARG_OFFSET 实现分页
     * - Android 11 以下（API 24-29）：在 sortOrder 末尾追加 LIMIT/OFFSET 子句，
     *   这是被广泛验证的兼容方案
     *
     * @param contentResolver 内容解析器
     * @param selection       WHERE 子句，传 null 表示无过滤条件
     * @param selectionArgs   WHERE 子句参数
     * @param page            页码（从 1 开始）
     * @param pageSize        每页数量
     * @return 当前页的图片列表，查询失败时返回空列表
     */
    private fun queryImagesWithPaging(
        contentResolver: ContentResolver,
        selection: String?,
        selectionArgs: Array<String>?,
        page: Int,
        pageSize: Int
    ): List<ImageItem> {
        // 根据页码和每页数量计算偏移量
        val offset = (page - 1) * pageSize

        val cursor: Cursor?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用官方推荐的 Bundle 分页 API
            val queryArgs = Bundle()
            queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, DEFAULT_SORT_ORDER)
            queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, pageSize)
            queryArgs.putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            if (selection != null) {
                queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }
            cursor = contentResolver.query(IMAGES_URI, IMAGE_PROJECTION, queryArgs, null)
        } else {
            // Android 10 及以下：在 sortOrder 中追加 LIMIT 和 OFFSET 子句
            // 底层 SQLite 数据库原生支持该语法，这是被广泛使用的兼容方案
            val sortWithPaging = "$DEFAULT_SORT_ORDER LIMIT $pageSize OFFSET $offset"
            cursor = contentResolver.query(IMAGES_URI, IMAGE_PROJECTION, selection, selectionArgs, sortWithPaging)
        }

        if (cursor == null) {
            return emptyList()
        }

        return cursor.use { c -> parseImagesFromCursor(c) }
    }

    /**
     * 从 Cursor 中解析图片数据
     *
     * 逐行读取 Cursor 中的各列数据，将每一行转换为 ImageItem 对象。
     * 图片的 URI 通过 ContentUris.withAppendedId 构建为 content:// 格式，
     * 确保在所有 Android 版本上都能正常使用。
     *
     * @param cursor MediaStore 查询返回的 Cursor
     * @return 解析后的图片列表
     */
    private fun parseImagesFromCursor(cursor: Cursor): List<ImageItem> {
        val images = ArrayList<ImageItem>(cursor.count)

        // 预先获取所有列的索引，避免在循环中重复调用 getColumnIndexOrThrow
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
        val mimeTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
        val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)

            // 通过 ContentUris 构建 content:// URI
            // 格式为 content://media/external/images/media/{id}
            // 这是跨版本兼容的标准方式，可直接用于 Glide / Coil 等图片框架
            val uri = ContentUris.withAppendedId(IMAGES_URI, id)

            val item = ImageItem(
                id = id,
                uri = uri,
                displayName = cursor.getString(displayNameCol).orEmpty(),
                size = cursor.getLong(sizeCol),
                dateAdded = cursor.getLong(dateAddedCol),
                dateModified = cursor.getLong(dateModifiedCol),
                mimeType = cursor.getString(mimeTypeCol) ?: "image/*",
                width = cursor.getInt(widthCol),
                height = cursor.getInt(heightCol),
                bucketId = cursor.getLong(bucketIdCol),
                bucketName = cursor.getString(bucketNameCol).orEmpty()
            )
            images.add(item)
        }

        return images
    }

    /**
     * 构建分页结果对象
     *
     * 根据当前页数据、页码、每页数量和总数计算完整的分页导航信息。
     *
     * @param data       当前页的数据列表
     * @param page       当前页码（从 1 开始）
     * @param pageSize   每页数量
     * @param totalCount 满足查询条件的数据总条数
     * @return 包含完整分页信息的 PagedResult 对象
     */
    private fun <T> buildPagedResult(
        data: List<T>,
        page: Int,
        pageSize: Int,
        totalCount: Int
    ): PagedResult<T> {
        // 使用向上取整公式计算总页数，避免浮点运算
        val totalPages = if (totalCount == 0) 0 else (totalCount + pageSize - 1) / pageSize

        return PagedResult(
            data = data,
            page = page,
            pageSize = pageSize,
            totalCount = totalCount,
            totalPages = totalPages,
            hasNextPage = page < totalPages,
            hasPreviousPage = page > 1
        )
    }
}