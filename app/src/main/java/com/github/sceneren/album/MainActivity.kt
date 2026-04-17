package com.github.sceneren.album

import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.setSingletonImageLoaderFactory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.crossfade
import com.github.sceneren.album.refresh.LoadMoreState
import com.github.sceneren.album.refresh.RefreshLazyVerticalGrid
import com.github.sceneren.album.ui.theme.AlbumTheme
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AlbumLoader.init(this)
        FileHelper.init(this)
        setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .crossfade(true)
                    .components {
                        if (SDK_INT >= 28) {
                            add(AnimatedImageDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                    }
                    .build()
            }
            AlbumTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel = viewModel<AlbumViewModel>()
                    val directoryList = viewModel.imageDirectoriesState.collectAsStateWithLifecycle()
                    val imageList = viewModel.imageListState.collectAsStateWithLifecycle()
                    val selectedDir = viewModel.currentDir.collectAsStateWithLifecycle()
                    val loadMoreState = viewModel.loadMoreState.collectAsStateWithLifecycle()
                    val hasMoreData = viewModel.hasMore.collectAsStateWithLifecycle()

                    TestAlbum(
                        innerPadding = innerPadding,
                        dirList = directoryList.value,
                        imageList = imageList.value,
                        selectedDir = selectedDir.value,
                        loadMoreState = loadMoreState.value,
                        hasMoreData = hasMoreData.value,
                        onLoadMore = viewModel::loadMoreImages,
                        onRequestPermissionSuccess = viewModel::getImageDirectories,
                        onRequestPermissionFail = {
                            Toast.makeText(this, "权限申请失败", Toast.LENGTH_SHORT).show()
                        },
                        onChooseDir = viewModel::setCurrentDir
                    )
                }
            }
        }
    }
}

@Composable
fun TestAlbum(
    innerPadding: PaddingValues,
    dirList: List<ImageDirectory>,
    imageList: List<ImageItem>,
    selectedDir: ImageDirectory?,
    loadMoreState: LoadMoreState,
    hasMoreData: Boolean,
    onLoadMore: () -> Unit,
    onRequestPermissionSuccess: () -> Unit,
    onRequestPermissionFail: () -> Unit,
    onChooseDir: (ImageDirectory) -> Unit,
) {
    val activity = LocalActivity.current
    var expanded by remember { mutableStateOf(false) }
    val lazyGridState = rememberLazyGridState()

    /**
     * 执行权限检查/申请并在成功后触发加载。
     *
     * 逻辑：
     * 1）已有权限：直接加载目录（ViewModel 内会自动选中“全部图片”并加载第一页）
     * 2）无权限：先申请权限，成功后走同一加载流程
     */
    fun checkOrRequestPermissionAndLoad() {
        val currentActivity = activity ?: return

        val hasPermission = XXPermissions.isGrantedPermission(
            currentActivity,
            PermissionLists.getReadMediaImagesPermission()
        )

        if (hasPermission) {
            onRequestPermissionSuccess()
            return
        }

        XXPermissions.with(currentActivity)
            .permission(PermissionLists.getReadMediaImagesPermission())
            .request { _, deniedList ->
                val allGranted = deniedList.isEmpty()
                if (allGranted) {
                    onRequestPermissionSuccess()
                } else {
                    onRequestPermissionFail()
                }
            }
    }

    // 进入界面后自动执行权限流程
    LaunchedEffect(Unit) {
        checkOrRequestPermissionAndLoad()
    }

    // 切换目录后，将图片列表滚动到顶部
    LaunchedEffect(selectedDir) {
        lazyGridState.scrollToItem(0)
    }

    Column(modifier = Modifier.padding(innerPadding)) {
        Button(onClick = { checkOrRequestPermissionAndLoad() }) {
            Text("申请权限")
        }

        // 目录选择
        Box {
            Text(
                modifier = Modifier
                    .clickable { expanded = true }
                    .border(width = 1.dp, color = Color.Black, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                text = "当前选择的目录是：${selectedDir?.bucketName}"
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                dirList.forEach {
                    DropdownMenuItem(
                        text = { Text(text = it.bucketName) },
                        onClick = {
                            expanded = false
                            onChooseDir(it)
                        }
                    )
                }
            }
        }

        // 图片数量提示
        Card(modifier = Modifier.padding(8.dp)) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = "当前目录下已经加载了：${imageList.size}张图片"
            )
        }

        // 图片列表
        RefreshLazyVerticalGrid(
            columns = GridCells.Fixed(4),
            lazyGridState = lazyGridState,
            loadMoreState = loadMoreState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            hasMoreData = hasMoreData,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            onLoadMore = onLoadMore
        ) {
            items(imageList) { image ->
                ImageItemView(image)
            }
        }
    }
}

@Composable
fun ImageItemView(image: ImageItem) {
    val scope = rememberCoroutineScope()
    var filePath by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        filePath = FileHelper.getFileUrl(image.uri)
                    }
                }
                .padding(8.dp)
        ) {
            Box {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp)),
                    model = image.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )

                if (image.isWebp) {
                    Text(
                        text = "WebP",
                        modifier = Modifier
                            .padding(end = 10.dp, bottom = 10.dp)
                            .background(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .align(Alignment.BottomEnd)
                    )
                }

                if (image.isGif) {
                    Text(
                        text = "GIF",
                        modifier = Modifier
                            .padding(end = 10.dp, bottom = 10.dp)
                            .background(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Text(text = image.displayName)
            Text(text = "文件路径：$filePath")
        }
    }
}