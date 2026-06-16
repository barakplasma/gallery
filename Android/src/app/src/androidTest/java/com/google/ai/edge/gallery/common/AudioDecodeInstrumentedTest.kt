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

package com.google.ai.edge.gallery.common

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.gallery.data.SAMPLE_RATE
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for [decodeAudioToAudioClip].
 *
 * Requires a real device or emulator because [android.media.MediaCodec] and
 * [android.media.MediaExtractor] are not available in the JVM unit-test sandbox.
 *
 * The test asset `test_audio.wav` is a 1-second 440 Hz sine wave at 16 kHz mono 16-bit PCM.
 * It exercises the WAV fast-path and verifies the resulting [AudioClip] has the correct
 * sample rate and non-trivial audio data.
 */
@RunWith(AndroidJUnit4::class)
class AudioDecodeInstrumentedTest {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private fun assetUri(filename: String): Uri {
    val outFile = File(context.cacheDir, filename)
    context.assets.open(filename).use { input ->
      outFile.outputStream().use { output -> input.copyTo(output) }
    }
    return Uri.fromFile(outFile)
  }

  @Test
  fun wavAsset_decodesTo16kHzMonoAudioClip() {
    val uri = assetUri("test_audio.wav")
    val clip = decodeAudioToAudioClip(context = context, uri = uri, mimeType = "audio/wav")

    assertNotNull("decodeAudioToAudioClip returned null for WAV asset", clip)
    assertEquals("Sample rate must be $SAMPLE_RATE", SAMPLE_RATE, clip!!.sampleRate)
    assertTrue("audioData must not be empty", clip.audioData.isNotEmpty())

    // 1 second at 16 kHz × 2 bytes/sample; allow ±5% tolerance for rounding
    val expectedBytes = SAMPLE_RATE * 2
    val tolerance = (expectedBytes * 0.05).toInt()
    assertTrue(
      "audioData size ${clip.audioData.size} outside ±5% of $expectedBytes",
      kotlin.math.abs(clip.audioData.size - expectedBytes) <= tolerance,
    )
  }

  @Test
  fun wavAsset_viaConvertWavPath_returnsSameResult() {
    val uri = assetUri("test_audio.wav")
    val fromDecode = decodeAudioToAudioClip(context = context, uri = uri, mimeType = "audio/wav")
    val fromConvert = convertWavToMonoWithMaxSeconds(context = context, stereoUri = uri)

    assertNotNull(fromDecode)
    assertNotNull(fromConvert)
    assertEquals(fromConvert!!.sampleRate, fromDecode!!.sampleRate)
    assertEquals(fromConvert.audioData.size, fromDecode.audioData.size)
  }
}
