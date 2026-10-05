package com.rockbyte.vicu.di

import com.rockbyte.vicu.page.AudioConvertViewModel
import com.rockbyte.vicu.page.AudioExportViewModel
import com.rockbyte.vicu.page.imagecrop.ImageCropViewModel
import com.rockbyte.vicu.page.imagescale.ImageScaleViewModel
import com.rockbyte.vicu.repo.ImageCropRepo
import com.rockbyte.vicu.repo.ImageCropRepository
import com.rockbyte.vicu.repo.ImageCropStore
import com.rockbyte.vicu.repo.ImageCropStorage
import com.rockbyte.vicu.repo.ImageScaleRepo
import com.rockbyte.vicu.repo.ImageScaleRepository
import com.rockbyte.vicu.page.HomeViewModel
import com.rockbyte.vicu.page.VideoConvertViewModel
import com.rockbyte.vicu.page.AudioReplaceViewModel
import com.rockbyte.vicu.page.crop.CropViewModel
import com.rockbyte.vicu.page.trim.TrimViewModel
import com.rockbyte.vicu.page.audiotrim.AudioTrimViewModel
import com.rockbyte.vicu.repo.AudioTrimRepo
import com.rockbyte.vicu.repo.AudioTrimRepository
import com.rockbyte.vicu.repo.AudioTrimStore
import com.rockbyte.vicu.repo.AudioTrimStorage
import com.rockbyte.vicu.repo.AudioPreviewRepo
import com.rockbyte.vicu.repo.AudioPreviewRepository
import com.rockbyte.vicu.repo.AudioPreviewStore
import com.rockbyte.vicu.repo.AudioPreviewStorage
import com.rockbyte.vicu.repo.AudioWaveformStore
import com.rockbyte.vicu.repo.AudioWaveformStorage
import com.rockbyte.vicu.repo.AudioWaveformRepo
import com.rockbyte.vicu.repo.AudioWaveformRepository
import com.rockbyte.vicu.page.player.PlayerViewModel
import com.rockbyte.vicu.repo.AudioConvertRepo
import com.rockbyte.vicu.repo.AudioConvertRepository
import com.rockbyte.vicu.repo.AudioConvertStore
import com.rockbyte.vicu.repo.AudioConvertStorage
import com.rockbyte.vicu.repo.AudioExportRepo
import com.rockbyte.vicu.repo.AudioExportRepository
import com.rockbyte.vicu.repo.AudioEncoder
import com.rockbyte.vicu.repo.FFmpegAudioEncoder
import com.rockbyte.vicu.repo.AudioReplaceRepo
import com.rockbyte.vicu.repo.AudioReplaceRepository
import com.rockbyte.vicu.repo.AudioOutputStore
import com.rockbyte.vicu.repo.AudioOutputStorage
import com.rockbyte.vicu.repo.FFmpegVideoConverter
import com.rockbyte.vicu.repo.MediaRepo
import com.rockbyte.vicu.repo.MediaRepository
import com.rockbyte.vicu.repo.MediaLibraryStore
import com.rockbyte.vicu.repo.MediaLibraryStorage
import com.rockbyte.vicu.player.PlayerRepo
import com.rockbyte.vicu.player.PlayerRepository
import com.rockbyte.vicu.repo.VideoConverter
import com.rockbyte.vicu.repo.VideoConvertRepo
import com.rockbyte.vicu.repo.VideoConvertRepository
import com.rockbyte.vicu.repo.VideoOutputStore
import com.rockbyte.vicu.repo.VideoOutputStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<MediaLibraryStore> { MediaLibraryStorage(androidContext()) }
    single<ImageCropStore> {
        ImageCropStorage(androidContext().contentResolver, System::currentTimeMillis) { Runtime.getRuntime().maxMemory() }
    }
    single<ImageCropRepo> { ImageCropRepository(get()) }
    viewModel { ImageCropViewModel(get()) }
    single<ImageScaleRepo> { ImageScaleRepository(get()) }
    viewModel { ImageScaleViewModel(get()) }
    single<MediaRepo> { MediaRepository(androidContext().contentResolver, get()) }
    single<AudioEncoder> { FFmpegAudioEncoder(androidContext()) }
    single<AudioOutputStore> { AudioOutputStorage(androidContext().contentResolver, System::currentTimeMillis) }
    single<AudioExportRepo> { AudioExportRepository(get(), get()) }
    single<AudioConvertStore> { AudioConvertStorage(androidContext().contentResolver, System::currentTimeMillis) }
    single<AudioConvertRepo> { AudioConvertRepository(get(), get()) }
    single<AudioTrimStore> { AudioTrimStorage(androidContext(), System::currentTimeMillis) }
    single<AudioTrimRepo> { AudioTrimRepository(get(), get()) }
    // Playback sessions belong to one page; sharing them would mix ranges across back-stack entries.
    factory<AudioPreviewStore> { AudioPreviewStorage(androidContext()) }
    factory<AudioPreviewRepo> { AudioPreviewRepository(get()) }
    single<AudioWaveformStore> { AudioWaveformStorage(androidContext()) }
    single<AudioWaveformRepo> { AudioWaveformRepository(get()) }
    viewModel { AudioTrimViewModel(get(), get(), get()) }
    single<VideoConverter> { FFmpegVideoConverter(androidContext()) }
    single<VideoOutputStore> { VideoOutputStorage(androidContext().contentResolver, System::currentTimeMillis) }
    single<VideoConvertRepo> { VideoConvertRepository(get(), get()) }
    single<AudioReplaceRepo> { AudioReplaceRepository(get(), get(), get()) }
    single<PlayerRepo> { PlayerRepository(androidContext()) }
    viewModel { HomeViewModel(androidContext(), get()) }
    viewModel { AudioExportViewModel(get()) }
    viewModel { VideoConvertViewModel(get()) }
    viewModel { AudioReplaceViewModel(get()) }
    viewModel { AudioConvertViewModel(get()) }
    viewModel { PlayerViewModel(get()) }
    viewModel { CropViewModel(get()) }
    viewModel { TrimViewModel(get()) }
}
