package com.github.sceneren.album

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.github.sceneren.album.ui.theme.AlbumTheme
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AlbumLoader.init(this)
        setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .crossfade(true)
                    .build()
            }
            AlbumTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel = viewModel<AlbumViewModel>()
                    val directoryList = viewModel.imageDirectoriesState.collectAsStateWithLifecycle()
                    val imageList = viewModel.imageListState.collectAsStateWithLifecycle()
                    TestAlbum(
                        innerPadding = innerPadding,
                        dirList = directoryList.value,
                        imageList = imageList.value,
                        onRequestPermissionSuccess = {},
                        onRequestPermissionFail = {}
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
    onRequestPermissionSuccess: () -> Unit,
    onRequestPermissionFail: () -> Unit
) {

    val activity = LocalActivity.current

    var expanded by remember { mutableStateOf(false) }

    var selectedDir by remember { mutableStateOf<ImageDirectory?>(null) }

    Column(modifier = Modifier.padding(innerPadding)) {
        Button(onClick = {
            activity ?: return@Button
            XXPermissions.with(activity)
                .permission(PermissionLists.getReadMediaImagesPermission())
                .request { _, deniedList ->
                    val allGranted = deniedList.isEmpty()
                    if (allGranted) {
                        onRequestPermissionSuccess()
                    } else {
                        onRequestPermissionFail()
                    }
                }
        }) { Text("申请权限") }

        Box {
            Text(
                modifier = Modifier
                    .clickable {
                        expanded = true
                    }
                    .border(width = 1.dp, color = Color.Black, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                text = "当前选择的目录是:${selectedDir?.bucketName}"
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {
                dirList.forEach {
                    DropdownMenuItem(
                        text = { Text(text = it.bucketName) },
                        onClick = {
                            selectedDir = it
                            expanded = false
                        }
                    )
                }
            }
        }


    }
}