package com.github.sceneren.album

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * 在 Android 设备上执行的仪器化测试。
 *
 * 具体用法参见[测试文档](http://d.android.com/tools/testing)。
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    /** 执行 `useAppContext` 方法定义的处理。 */
    fun useAppContext() {
        // 获取当前被测应用的上下文。
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.github.sceneren.album", appContext.packageName)
    }
}
