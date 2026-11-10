package com.github.sceneren.photopicker

import com.github.sceneren.album.api.AlbumMedia

/** Keep preview originals separate from thumbnails and invalidate when known source metadata changes. */
internal fun thumbnailCacheKey(media: AlbumMedia): String =
    "album-thumbnail-v2:${media.uri}:${media.generationModified}:${media.dateModifiedEpochSeconds}:${media.sizeBytes}"

/** A new namespace also invalidates previews previously decoded from cropped provider assets. */
internal fun previewCacheKey(media: AlbumMedia): String =
    "album-preview-original-v3:${media.uri}:${media.generationModified}:${media.dateModifiedEpochSeconds}:${media.sizeBytes}"
