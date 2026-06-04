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

import com.google.ai.edge.gallery.data.SAMPLE_RATE
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WavConversionTest {

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Builds a minimal valid WAV byte array.
   *
   * @param channels  1 = mono, 2 = stereo
   * @param sampleRate  e.g. 16000, 8000, 44100
   * @param bitDepth  8 or 16
   * @param durationSeconds  length of the audio in seconds
   */
  private fun buildWavBytes(
    channels: Int,
    sampleRate: Int,
    bitDepth: Int,
    durationSeconds: Int,
  ): ByteArray {
    val bytesPerSample = bitDepth / 8
    val numSamples = sampleRate * durationSeconds * channels
    val dataSize = numSamples * bytesPerSample
    val totalSize = 44 + dataSize

    val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

    // RIFF header
    buf.put("RIFF".toByteArray())
    buf.putInt(totalSize - 8)
    buf.put("WAVE".toByteArray())

    // fmt sub-chunk
    buf.put("fmt ".toByteArray())
    buf.putInt(16) // sub-chunk1 size for PCM
    buf.putShort(1) // PCM format
    buf.putShort(channels.toShort())
    buf.putInt(sampleRate)
    buf.putInt(sampleRate * channels * bytesPerSample) // byte rate
    buf.putShort((channels * bytesPerSample).toShort()) // block align
    buf.putShort(bitDepth.toShort())

    // data sub-chunk header
    buf.put("data".toByteArray())
    buf.putInt(dataSize)

    // PCM data: a simple 440 Hz sine wave
    val angularFreq = 2.0 * Math.PI * 440.0 / sampleRate
    for (i in 0 until (numSamples / channels)) {
      val sample = (sin(angularFreq * i) * 10000).toInt()
      repeat(channels) {
        if (bitDepth == 16) {
          buf.putShort(sample.toShort())
        } else {
          // 8-bit unsigned: center at 128
          buf.put(((sample / 256) + 128).toByte())
        }
      }
    }

    return buf.array()
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  @Test
  fun `mono 16kHz 16-bit WAV produces AudioClip at 16000 Hz`() {
    val bytes = buildWavBytes(channels = 1, sampleRate = 16000, bitDepth = 16, durationSeconds = 1)
    val clip = convertWavToMonoWithMaxSeconds(ByteArrayInputStream(bytes))
    assertNotNull(clip)
    assertEquals(16000, clip!!.sampleRate)
    // 1 second × 16000 samples × 2 bytes each
    assertEquals(16000 * 2, clip.audioData.size)
  }

  @Test
  fun `stereo 16kHz 16-bit WAV is downmixed to mono`() {
    val bytes = buildWavBytes(channels = 2, sampleRate = 16000, bitDepth = 16, durationSeconds = 1)
    val clip = convertWavToMonoWithMaxSeconds(ByteArrayInputStream(bytes))
    assertNotNull(clip)
    assertEquals(16000, clip!!.sampleRate)
    // Stereo → mono halves the sample count
    assertEquals(16000 * 2, clip.audioData.size)
  }

  @Test
  fun `8kHz mono WAV is resampled to 16kHz`() {
    val bytes = buildWavBytes(channels = 1, sampleRate = 8000, bitDepth = 16, durationSeconds = 1)
    val clip = convertWavToMonoWithMaxSeconds(ByteArrayInputStream(bytes))
    assertNotNull(clip)
    assertEquals(16000, clip!!.sampleRate)
    // Resampled 2× → 16000 samples
    assertEquals(16000 * 2, clip.audioData.size)
  }

  @Test
  fun `8-bit mono WAV is converted to 16-bit output`() {
    val bytes = buildWavBytes(channels = 1, sampleRate = 16000, bitDepth = 8, durationSeconds = 1)
    val clip = convertWavToMonoWithMaxSeconds(ByteArrayInputStream(bytes))
    assertNotNull(clip)
    assertEquals(16000, clip!!.sampleRate)
    // Output is always 16-bit PCM
    assertEquals(16000 * 2, clip.audioData.size)
  }

  @Test
  fun `clip longer than maxSeconds is trimmed`() {
    val bytes = buildWavBytes(channels = 1, sampleRate = 16000, bitDepth = 16, durationSeconds = 35)
    val clip = convertWavToMonoWithMaxSeconds(ByteArrayInputStream(bytes), maxSeconds = 30)
    assertNotNull(clip)
    // Exactly 30 seconds × 16000 samples × 2 bytes
    assertEquals(30 * 16000 * 2, clip!!.audioData.size)
  }

  @Test
  fun `input shorter than 44 bytes returns null`() {
    val bytes = ByteArray(20)
    val clip = convertWavToMonoWithMaxSeconds(ByteArrayInputStream(bytes))
    assertNull(clip)
  }

  @Test
  fun `stereo average is correct for known values`() {
    // Build a 1-sample stereo WAV: left=1000, right=3000 → mono should be 2000
    val buf = ByteBuffer.allocate(44 + 4).order(ByteOrder.LITTLE_ENDIAN)
    buf.put("RIFF".toByteArray())
    buf.putInt(40)
    buf.put("WAVE".toByteArray())
    buf.put("fmt ".toByteArray())
    buf.putInt(16)
    buf.putShort(1) // PCM
    buf.putShort(2) // stereo
    buf.putInt(16000)
    buf.putInt(16000 * 2 * 2) // byte rate
    buf.putShort(4) // block align
    buf.putShort(16) // bit depth
    buf.put("data".toByteArray())
    buf.putInt(4) // 1 stereo sample = 4 bytes
    buf.putShort(1000)
    buf.putShort(3000)

    val clip = convertWavToMonoWithMaxSeconds(ByteArrayInputStream(buf.array()))
    assertNotNull(clip)
    val out = ByteBuffer.wrap(clip!!.audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    assertEquals(1, out.limit())
    // (1000 + 3000) / 2 == 2000
    val sample = out.get().toInt()
    assertTrue("Expected ~2000 but was $sample", abs(sample - 2000) <= 1)
  }
}
