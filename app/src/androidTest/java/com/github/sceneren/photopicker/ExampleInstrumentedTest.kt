package com.github.sceneren.photopicker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 在 Android 设备上执行的仪器化测试。
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    /** 执行 `useAppContext` 方法定义的处理。 */
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.github.sceneren.photopicker", appContext.packageName)
    }
}
