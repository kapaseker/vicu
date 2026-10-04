package com.rockbyte.vicu.page.audiotrim

import com.rockbyte.vicu.repo.AudioWaveform
import org.junit.Assert.*
import org.junit.Test

class WaveformViewportTest {
    @Test fun underestimatedMetadataDoesNotMakeDecodedTailUnreachable() {
        val waveform = AudioWaveform(FloatArray(200), 10.0, 2000.0)
        val extent = waveformExtentMs(waveform, 1000)
        assertEquals(2000L, extent)
        assertEquals(2000.0, WaveformViewport.full(extent).timeAt(1.0), 0.001)
        assertEquals(1000.0, WaveformViewport(0.0, 1000.0).pan(-2.0, extent).startMs, 0.001)
        assertEquals(3000L, waveformExtentMs(waveform, 3000))
    }

    @Test fun zoomKeepsTheGestureAnchorAndClampsToOneSecond() {
        val zoomed = WaveformViewport.full(10000).zoom(2.0, 0.25, 10000)
        assertEquals(1250.0, zoomed.startMs, 0.001)
        assertEquals(5000.0, zoomed.spanMs, 0.001)
        assertEquals(2500.0, zoomed.timeAt(0.25), 0.001)
        assertEquals(1000.0, zoomed.zoom(100.0, 0.5, 10000).spanMs, 0.001)
        assertEquals(500.0, WaveformViewport.full(500).zoom(100.0, 0.5, 500).spanMs, 0.001)
    }

    @Test fun panClampsAtFileEdgesAndDisablesFollowing() {
        val viewport = WaveformViewport(2000.0, 1000.0)
        assertEquals(0.0, viewport.pan(10.0, 10000).startMs, 0.001)
        assertEquals(9000.0, viewport.pan(-10.0, 10000).startMs, 0.001)
        assertFalse(viewport.pan(0.1, 10000).following)
        assertEquals(0.5, viewport.fractionAt(2500), 0.001)
    }

    @Test fun playbackPagesAtTheEdgeButDoesNotOverrideManualBrowsing() {
        val viewport = WaveformViewport(0.0, 2000.0)
        assertEquals(2000.0, viewport.follow(2000, true, 10000).startMs, 0.001)
        val browsing = viewport.pan(-0.5, 10000)
        assertEquals(browsing, browsing.follow(8000, true, 10000))
        val located = browsing.locate(8000, 10000)
        assertTrue(located.following)
        assertTrue(8000.0 in located.startMs..located.endMs)
        assertEquals(viewport, viewport.follow(9000, false, 10000))
    }

    @Test fun overlappingHandlesUseDistanceThenDragDirectionAndIgnoreOffscreenEndpoints() {
        assertEquals(true, waveformHandleAt(50f, 48f, 55f, 24f, 1f, 100f))
        assertEquals(true, waveformHandleAt(50f, 50f, 50f, 24f, -1f, 100f))
        assertEquals(false, waveformHandleAt(50f, 50f, 50f, 24f, 1f, 100f))
        assertNull(waveformHandleAt(50f, 0f, 100f, 24f, 1f, 100f))
        assertNull(waveformHandleAt(0f, -5f, 100f, 24f, -1f, 100f))
    }

    @Test fun displayAggregationPreservesBriefPeaksAndUsesFixedAmplitudeAtEveryZoom() {
        val waveform = AudioWaveform(floatArrayOf(0f, 0.9f, 0.1f, 0f), 10.0, 40.0)
        assertArrayEquals(floatArrayOf(0.9f, 0.1f), waveformBars(waveform, WaveformViewport.full(40), 2), 0f)
        assertArrayEquals(floatArrayOf(0.9f), waveformBars(waveform, WaveformViewport(10.0, 10.0), 1), 0f)
        assertArrayEquals(floatArrayOf(0f), waveformBars(waveform, WaveformViewport(50.0, 10.0), 1), 0f)
    }
}
