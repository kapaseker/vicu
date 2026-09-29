package com.rockbyte.vicu.repo

import android.content.ContentResolver
import android.content.Context
import android.media.AudioTrack
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.Surface
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
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

    @Test
    fun applyTrimSeeksToStartWhenPositionIsAtOrAfterEnd() = runBlocking {
        repository.open(media)
        player.listener?.onEvent(NativePlayerEvent.Position(9000))

        repository.applyEffects(listOf(EffectSpec.Trim(2000, 8000)))

        assertEquals(listOf(2000L), player.seeks)
    }

    @Test
    fun openRegistersAudioClockBeforePrepareCanCallback() = runBlocking {
        player.preparedEvent = NativePlayerEvent.Prepared(1920, 1080, 10000, hasAudio = false)

        repository.open(media)

        assertEquals(true, player.hadAudioClockWhenPrepared)
    }

    @Test
    fun surfaceAvailableBeforeOpenIsBoundToNewPlayer() = runBlocking {
        val surface = Mockito.mock(Surface::class.java)

        repository.setSurface(surface)
        repository.open(media)

        assertEquals(surface, player.boundSurface)
    }

    @Test
    fun audioTrackCreationFailureReportsPlaybackFailed() = runBlocking {
        val failingRepository = PlayerRepository(
            context = context,
            playerFactory = { player },
            audioTrackFactory = { error("AudioTrack unavailable") },
        )
        player.preparedEvent = NativePlayerEvent.Prepared(1920, 1080, 10000, hasAudio = true)
        val failure = async(start = CoroutineStart.UNDISPATCHED) {
            failingRepository.events.first()
        }

        failingRepository.open(media)

        assertEquals(PlayerEvent.Failed(PlayerError.PlaybackFailed), failure.await())
    }

    @Test
    fun audioTrackWriteFailureReportsPlaybackFailed() = runBlocking {
        val audioTrack = Mockito.mock(AudioTrack::class.java)
        val failingRepository = PlayerRepository(
            context = context,
            playerFactory = { player },
            audioTrackFactory = { audioTrack },
        )
        player.preparedEvent = NativePlayerEvent.Prepared(1920, 1080, 10000, hasAudio = true)
        Mockito.`when`(audioTrack.write(any(ByteArray::class.java), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(AudioTrack.ERROR_DEAD_OBJECT)
        val failure = async(start = CoroutineStart.UNDISPATCHED) {
            failingRepository.events.filterIsInstance<PlayerEvent.Failed>().first()
        }

        failingRepository.open(media)
        player.listener?.onEvent(NativePlayerEvent.AudioData(byteArrayOf(0, 0), 0, 0))

        assertEquals(PlayerEvent.Failed(PlayerError.PlaybackFailed), failure.await())
    }

    @Test
    fun backwardSeekInFlightAudioDoesNotPoisonClockBase() = runBlocking {
        val audioTrack = Mockito.mock(AudioTrack::class.java)
        Mockito.`when`(audioTrack.playState).thenReturn(AudioTrack.PLAYSTATE_PLAYING)
        val audioRepository = PlayerRepository(
            context = context,
            playerFactory = { player },
            audioTrackFactory = { audioTrack },
        )
        player.preparedEvent = NativePlayerEvent.Prepared(1920, 1080, 10000, hasAudio = true)
        audioRepository.open(media)

        // 播放中位于 60s，用户向后拖拽到 5s
        audioRepository.seekTo(5000)
        // seek 已发起但引擎尚未完成时，旧位置的滞留音频帧到达（旧代际 0；pts=60s ≥ 新目标 5s）
        player.listener?.onEvent(NativePlayerEvent.AudioData(byteArrayOf(0, 0), 60_000_000L, 0))
        // seek 后真正的首帧（perform_seek 已递增代际 → 1）
        player.listener?.onEvent(NativePlayerEvent.AudioData(byteArrayOf(0, 0), 5_020_000L, 1))

        assertEquals(5_020_000L, player.audioClockUs())
    }

    @Test
    fun seekDropsAudioFramesBeforeTarget() = runBlocking {
        val audioTrack = Mockito.mock(AudioTrack::class.java)
        val audioRepository = PlayerRepository(
            context = context,
            playerFactory = { player },
            audioTrackFactory = { audioTrack },
        )
        player.preparedEvent = NativePlayerEvent.Prepared(1920, 1080, 10000, hasAudio = true)
        audioRepository.open(media)

        audioRepository.seekTo(5000)
        player.listener?.onEvent(NativePlayerEvent.AudioData(byteArrayOf(0, 0), 4000000, 0))

        Mockito.verify(audioTrack, Mockito.never())
            .write(any(ByteArray::class.java), Mockito.anyInt(), Mockito.anyInt())
        Unit
    }

    private class FakeNativePlayer : NativePlayer {
        var listener: NativePlayer.Listener? = null
            private set

        var filterChain: String? = null
        var playRange: Pair<Long, Long>? = null
        val seeks = mutableListOf<Long>()
        var preparedEvent: NativePlayerEvent.Prepared? = null
        var hadAudioClockWhenPrepared = false
        var boundSurface: Surface? = null
        private var audioClockProvider: NativePlayer.AudioClockProvider? = null

        override fun setListener(listener: NativePlayer.Listener?) {
            this.listener = listener
        }

        override fun prepare(fd: Int): Int {
            preparedEvent?.let {
                hadAudioClockWhenPrepared = audioClockProvider != null
                listener?.onEvent(it)
            }
            return 0
        }
        override fun setSurface(surface: Surface?) {
            boundSurface = surface
        }
        override fun start() = Unit
        override fun pause() = Unit
        override fun seek(positionMs: Long) {
            seeks += positionMs
        }

        override fun setAudioClockProvider(provider: NativePlayer.AudioClockProvider?) {
            audioClockProvider = provider
        }
        fun audioClockUs(): Long = audioClockProvider?.audioClockUs() ?: -1L
        override fun setFilterGraph(chain: String) {
            filterChain = chain
        }

        override fun setPlayRange(startMs: Long, endMs: Long) {
            playRange = startMs to endMs
        }

        override fun release() = Unit
    }
}
