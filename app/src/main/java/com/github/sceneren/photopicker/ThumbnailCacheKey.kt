package com.github.sceneren.photopicker

import com.github.sceneren.album.api.AlbumMedia

/** Keep preview originals separate from thumbnails and invalidate when known source metadata changes. */
internal fun thumbnailCacheKey(media: AlbumMedia): String =
    "album-thumbnail-v1:${media.uri}:${media.dateModifiedEpochSeconds}:${media.sizeBytes}"
