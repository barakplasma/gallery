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

package com.google.ai.edge.gallery.data

import android.content.Intent
import android.net.Uri
import android.os.Build

/** Typed payload received from an Android share-target intent. */
sealed class ShareData {
  /** Plain text shared from another app. */
  data class Text(val text: String) : ShareData()

  /** A single image URI shared from another app. */
  data class Image(val uri: Uri) : ShareData()

  /**
   * A single audio file URI shared from another app.
   *
   * [mimeType] is preserved so the decode path can choose WAV vs. compressed (e.g. Opus/OGG).
   */
  data class Audio(val uri: Uri, val mimeType: String) : ShareData()
}

/** Maps a [ShareData] instance to the built-in task ID that handles that content type. */
fun shareDataToTaskId(shareData: ShareData): String =
  when (shareData) {
    is ShareData.Text -> BuiltInTaskId.LLM_CHAT
    is ShareData.Image -> BuiltInTaskId.LLM_ASK_IMAGE
    is ShareData.Audio -> BuiltInTaskId.LLM_ASK_AUDIO
  }

/**
 * Parses an [Intent] with [Intent.ACTION_SEND] into a [ShareData], or returns null if the intent
 * is not a supported share intent.
 *
 * This is a pure function (no Context required) so it can be unit-tested without Android
 * instrumentation.
 */
fun parseShareIntent(intent: Intent): ShareData? {
  if (intent.action != Intent.ACTION_SEND) return null
  val mimeType = intent.type ?: return null

  return when {
    mimeType == "text/plain" -> {
      val text = intent.getStringExtra(Intent.EXTRA_TEXT)
      if (!text.isNullOrEmpty()) ShareData.Text(text) else null
    }
    mimeType.startsWith("image/") -> {
      val uri = getParcelableUri(intent) ?: return null
      ShareData.Image(uri)
    }
    mimeType.startsWith("audio/") -> {
      val uri = getParcelableUri(intent) ?: return null
      ShareData.Audio(uri, mimeType)
    }
    else -> null
  }
}

@Suppress("DEPRECATION")
private fun getParcelableUri(intent: Intent): Uri? =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
  } else {
    intent.getParcelableExtra(Intent.EXTRA_STREAM)
  }
