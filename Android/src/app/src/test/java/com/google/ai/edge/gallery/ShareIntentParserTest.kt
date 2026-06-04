/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery

import android.content.Intent
import android.net.Uri
import com.google.ai.edge.gallery.data.ShareData
import com.google.ai.edge.gallery.data.parseShareIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareIntentParserTest {

  @Test
  fun `text share returns ShareData_Text with correct string`() {
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Hello from another app")
      }
    val result = parseShareIntent(intent)
    assertTrue(result is ShareData.Text)
    assertEquals("Hello from another app", (result as ShareData.Text).text)
  }

  @Test
  fun `image share returns ShareData_Image with correct uri`() {
    val uri = Uri.parse("content://media/external/images/1")
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
      }
    val result = parseShareIntent(intent)
    assertTrue(result is ShareData.Image)
    assertEquals(uri, (result as ShareData.Image).uri)
  }

  @Test
  fun `audio ogg share returns ShareData_Audio with correct uri and mimeType`() {
    val uri = Uri.parse("content://com.whatsapp.provider/media/audio/1")
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "audio/ogg"
        putExtra(Intent.EXTRA_STREAM, uri)
      }
    val result = parseShareIntent(intent)
    assertTrue(result is ShareData.Audio)
    assertEquals(uri, (result as ShareData.Audio).uri)
    assertEquals("audio/ogg", result.mimeType)
  }

  @Test
  fun `text share with null EXTRA_TEXT returns null`() {
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        // No EXTRA_TEXT set
      }
    assertNull(parseShareIntent(intent))
  }

  @Test
  fun `text share with empty EXTRA_TEXT returns null`() {
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "")
      }
    assertNull(parseShareIntent(intent))
  }

  @Test
  fun `ACTION_VIEW is not a share intent and returns null`() {
    val intent = Intent(Intent.ACTION_VIEW).apply { type = "text/plain" }
    assertNull(parseShareIntent(intent))
  }

  @Test
  fun `intent with null MIME type returns null`() {
    val intent = Intent(Intent.ACTION_SEND)
    // type is null by default
    assertNull(parseShareIntent(intent))
  }

  @Test
  fun `image share without EXTRA_STREAM returns null`() {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "image/png" }
    assertNull(parseShareIntent(intent))
  }
}
