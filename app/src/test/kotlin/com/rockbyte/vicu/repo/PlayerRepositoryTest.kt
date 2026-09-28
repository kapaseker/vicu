package com.rockbyte.vicu.repo

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.Surface
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.MockedStatic
import org.mockito.Mockito

/** applyEffects 的调用编排（crop → 滤镜链；trim → 播放区间 + 起点跳转）。 */
class PlayerRepositoryTest {

    private val player = FakeNativePlayer()
    private val context = Mockito.mock(Context::class.java)

    // mockable android.jar 的 Uri.parse 抛 "not mocked"：静态拦截返回 mock（仅当前线程生效）
    private val uri = Mockito.mock(Uri::class.java)
    private val uriStatic: MockedStatic<Uri> = Mockito.mockStatic(Uri::class.java)

    private val repository = PlayerRepository(
        context = context,
        playerFactory = { player },
    )

    private val media = SelectedMedia("content://media/video/1", "sample.mp4", MediaKind.VIDEO)

    @Before
    fun setUp() {
        // SAF fd 打开链路 mock：dup 出的 fd 所有权移交 fake player（prepare 空实现）
        val resolver = Mockito.mock(ContentResolver::class.java)
        val pfd = Mockito.mock(ParcelFileDescriptor::class.java)
        val dup = Mockito.mock(ParcelFileDescriptor::class.java)
        Mockito.`when`(context.contentResolver).thenReturn(resolver)
        Mockito.`when`(resolver.openFileDescriptor(any(Uri::class.java), any(String::class.java)))
            .thenReturn(pfd)
        Mockito.`when`(pfd.dup()).thenReturn(dup)
        Mockito.`when`(dup.detachFd()).thenReturn(42)
        uriStatic.`when`<Uri> { Uri.parse(any()) }.thenReturn(uri)
    }

    @After
    fun tearDown() {
        uriStatic.close()
    }

    @Test
    fun applyEffectsWithoutSessionIsNoOp() {
        repository.applyEffects(listOf(EffectSpec.Crop(0, 0, 100, 100)))
        assertNull(player.filterChain)
        assertNull(player.playRange)
    }

    @Test
    fun applyCropSetsFilterGraphOnly() = runBlocking {
        repository.open(media)

        repository.applyEffects(listOf(EffectSpec.Crop(10, 20, 100, 50)))

        assertEquals("crop=w=100:h=50:x=10:y=20", player.filterChain)
        // 无 trim 时重置播放区间（清理历史 trim 的区间限制）
        assertEquals(0L to -1L, player.playRange)
    }

    @Test
    fun applyTrimSetsPlayRange() = runBlocking {
        repository.open(media)

        repository.applyEffects(listOf(EffectSpec.Trim(1500, 3200)))

        assertEquals("", player.filterChain)
        assertEquals(1500L to 3200L, player.playRange)
    }

    @Test
    fun applyTrimSeeksToStartWhenPositionBeforeRange() = runBlocking {
        repository.open(media)
        player.listener?.onEvent(NativePlayerEvent.Position(1000))

        repository.applyEffects(listOf(EffectSpec.Trim(1500, 3200)))

        assertEquals(listOf(1500L), player.seeks)
    }

    @Test
    fun applyTrimKeepsPositionWhenInsideRange() = runBlocking {
        repository.open(media)
        player.listener?.onEvent(NativePlayerEvent.Position(2000))

        repository.applyEffects(listOf(EffectSpec.Trim(1500, 3200)))

        assertEquals(emptyList<Long>(), player.seeks)
    }

    @Test
    fun clearingTrimResetsPlayRange() = runBlocking {
        repository.open(media)
        repository.applyEffects(listOf(EffectSpec.Trim(1500, 3200)))

        repository.applyEffects(emptyList())

        assertEquals(0L to -1L, player.playRange)
    }

    @Test
    fun positionEventsUpdateForTrimStartJudgement() = runBlocking {
        repository.open(media)
        // 先推进进度再应用更晚的区间起点 → 触发跳转
        player.listener?.onEvent(NativePlayerEvent.Position(3000))
        repository.applyEffects(listOf(EffectSpec.Trim(4000, 9000)))

        assertEquals(listOf(4000L), player.seeks)
    }

    private class FakeNativePlayer : NativePlayer {
        var listener: NativePlayer.Listener? = null
            private set

        var filterChain: String? = null
        var playRange: Pair<Long, Long>? = null
        val seeks = mutableListOf<Long>()

        override fun setListener(listener: NativePlayer.Listener?) {
            this.listener = listener
        }

        override fun prepare(fd: Int): Int = 0
        override fun setSurface(surface: Surface?) = Unit
        override fun start() = Unit
        override fun pause() = Unit
        override fun seek(positionMs: Long) {
            seeks += positionMs
        }

        override fun setAudioClockProvider(provider: NativePlayer.AudioClockProvider?) = Unit
        override fun setFilterGraph(chain: String) {
            filterChain = chain
        }

        override fun setPlayRange(startMs: Long, endMs: Long) {
            playRange = startMs to endMs
        }

        override fun release() = Unit
    }
}
