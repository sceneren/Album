package com.github.sceneren.album

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.imageLoader
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.MediaAccessStatus
import com.github.sceneren.album.api.PhotoPickResult
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumScreen(
    state: AlbumUiState,
    media: LazyPagingItems<AlbumMedia>,
    onFilterChanged: (AlbumMediaFilter) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenPicker: () -> Unit,
    onDirectorySelected: (Long) -> Unit,
    onRetry: () -> Unit,
    imageLoader: ImageLoader = LocalContext.current.imageLoader,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            AlbumControls(
                state = state,
                onFilterChanged = onFilterChanged,
                onRequestPermission = onRequestPermission,
                onOpenPicker = onOpenPicker,
                onDirectorySelected = onDirectorySelected,
            )

            if (media.loadState.refresh is LoadState.Loading && media.itemCount > 0) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            MediaContent(
                media = media,
                imageLoader = imageLoader,
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AlbumControls(
    state: AlbumUiState,
    onFilterChanged: (AlbumMediaFilter) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenPicker: () -> Unit,
    onDirectorySelected: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AlbumMediaFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.mediaFilter == filter,
                    onClick = { onFilterChanged(filter) },
                    label = { Text(filter.label()) },
                )
            }
        }

        Text(
            text = stringResource(
                R.string.access_and_source,
                state.accessStatus.label(),
                state.source.label(),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.request_permission))
            }
            OutlinedButton(onClick = onOpenPicker) {
                Text(stringResource(R.string.open_photo_picker))
            }
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        PickerResultText(state.pickerResult)
    }

    if (state.source == AlbumMediaSource.MEDIA_STORE && state.directories.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = state.directories,
                key = AlbumDirectory::bucketId,
            ) { directory ->
                FilterChip(
                    selected = state.selectedBucketId == directory.bucketId,
                    onClick = { onDirectorySelected(directory.bucketId) },
                    label = {
                        Text(
                            text = directory.bucketName
                                ?: stringResource(R.string.all_media),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaContent(
    media: LazyPagingItems<AlbumMedia>,
    imageLoader: ImageLoader,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshState = media.loadState.refresh
    when {
        refreshState is LoadState.Loading && media.itemCount == 0 -> {
            CenteredMessage(modifier) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.loading))
            }
        }

        refreshState is LoadState.Error && media.itemCount == 0 -> {
            ErrorMessage(
                message = refreshState.error.message ?: stringResource(R.string.unknown_error),
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        refreshState is LoadState.NotLoading && media.itemCount == 0 -> {
            CenteredMessage(modifier) {
                Text(stringResource(R.string.empty_media))
            }
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    count = media.itemCount,
                    key = { index -> media[index]?.uri?.toString() ?: "placeholder-$index" },
                ) { index ->
                    media[index]?.let { item ->
                        MediaCard(item = item, imageLoader = imageLoader)
                    }
                }

                when (val appendState = media.loadState.append) {
                    is LoadState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                        ErrorMessage(
                            message = appendState.error.message
                                ?: stringResource(R.string.unknown_error),
                            onRetry = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is LoadState.NotLoading -> Unit
                }
            }
        }
    }
}

@Composable
private fun MediaCard(
    item: AlbumMedia,
    imageLoader: ImageLoader,
) {
    val overlayColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)
    val overlayContentColor = MaterialTheme.colorScheme.inverseOnSurface
    ElevatedCard {
        Column {
            Box {
                AsyncImage(
                    model = item.uri,
                    imageLoader = imageLoader,
                    contentDescription = item.displayName
                        ?: stringResource(R.string.media_content_description),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Crop,
                )
                if (item.mediaType == AlbumMediaType.VIDEO) {
                    Text(
                        text = stringResource(R.string.video_badge),
                        color = overlayContentColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(overlayColor)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                item.durationMillis?.let { duration ->
                    Text(
                        text = formatDuration(duration),
                        color = overlayContentColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(overlayColor)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = item.displayName.orEmpty(),
                modifier = Modifier.padding(8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CenteredMessage(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    CenteredMessage(modifier) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun PickerResultText(result: PhotoPickResult?) {
    val text = when (result) {
        null -> return
        is PhotoPickResult.Selected -> stringResource(R.string.picker_selected_count, result.media.size)
        PhotoPickResult.Cancelled -> stringResource(R.string.picker_cancelled)
        is PhotoPickResult.Failed -> stringResource(R.string.picker_failed, result.reason.name)
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun AlbumMediaFilter.label(): String = when (this) {
    AlbumMediaFilter.IMAGES -> stringResource(R.string.filter_images)
    AlbumMediaFilter.VIDEOS -> stringResource(R.string.filter_videos)
    AlbumMediaFilter.IMAGES_AND_VIDEOS -> stringResource(R.string.filter_images_and_videos)
}

@Composable
private fun MediaAccessStatus.label(): String = when (this) {
    MediaAccessStatus.FULL -> stringResource(R.string.access_full)
    MediaAccessStatus.PARTIAL -> stringResource(R.string.access_partial)
    MediaAccessStatus.DENIED -> stringResource(R.string.access_denied)
}

@Composable
private fun AlbumMediaSource.label(): String = when (this) {
    AlbumMediaSource.MEDIA_STORE -> stringResource(R.string.source_media_store)
    AlbumMediaSource.PHOTO_PICKER -> stringResource(R.string.source_photo_picker)
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
