package com.github.sceneren.album.ui.view

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.github.sceneren.album.api.AlbumPickerIntentCodec
import com.github.sceneren.album.api.AlbumPickerResult

/** 以全屏 View Activity 打开相册选择器。 */
class AlbumPickerContract : ActivityResultContract<AlbumPickerRequest, AlbumPickerResult?>() {
    /** 创建或准备 `createIntent` 对应的对象。 */
    override fun createIntent(context: Context, input: AlbumPickerRequest): Intent =
        Intent(context, AlbumPickerActivity::class.java)
            .also { intent ->
                AlbumPickerIntentCodec.putConfig(intent, input.config)
                intent.putExtra(AlbumPickerExtras.THEME, input.themeResId)
                AlbumPickerExtras.putAppearance(intent, input.appearance)
                AlbumPickerExtras.putAnimation(intent, input.animation)
                intent.putExtra(
                    AlbumPickerIntentCodec.EXTRA_SESSION_ID,
                    AlbumPickerIntentCodec.newSessionId(),
                )
            }

    /** 执行 `parseResult` 方法定义的处理。 */
    override fun parseResult(resultCode: Int, intent: Intent?): AlbumPickerResult? =
        if (resultCode == android.app.Activity.RESULT_OK && intent != null) {
            AlbumPickerIntentCodec.readResult(intent)
        } else {
            null
    }
}
