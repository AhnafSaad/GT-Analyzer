package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GT Wifi Analyzer", appName)
  }

  @Test
  fun `verify translations dictionary keys exist`() {
    val bnTitle = com.example.localization.Translations.tr("appName", com.example.localization.AppLanguage.BN)
    val enTitle = com.example.localization.Translations.tr("appName", com.example.localization.AppLanguage.EN)
    assertEquals("GT Wifi Analyzer", bnTitle)
    assertEquals("GT Wifi Analyzer", enTitle)
  }
}
