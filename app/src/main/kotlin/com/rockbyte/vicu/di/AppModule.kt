package com.rockbyte.vicu.di

import com.rockbyte.vicu.page.AudioExportViewModel
import com.rockbyte.vicu.page.HomeViewModel
import com.rockbyte.vicu.page.VideoConvertViewModel
import com.rockbyte.vicu.repo.AudioExportRepo
import com.rockbyte.vicu.repo.AudioExportRepository
import com.rockbyte.vicu.repo.AudioEncoder
import com.rockbyte.vicu.repo.FFmpegAudioEncoder
import com.rockbyte.vicu.repo.AudioOutputStore
import com.rockbyte.vicu.repo.AudioOutputStorage
import com.rockbyte.vicu.repo.FFmpegVideoConverter
import com.rockbyte.vicu.repo.MediaRepo
import com.rockbyte.vicu.repo.MediaRepository
import com.rockbyte.vicu.repo.MediaLibraryStore
import com.rockbyte.vicu.repo.MediaLibraryStorage
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
    single<MediaRepo> { MediaRepository(androidContext().contentResolver, get()) }
    single<AudioEncoder> { FFmpegAudioEncoder(androidContext()) }
    single<AudioOutputStore> { AudioOutputStorage(androidContext().contentResolver, System::currentTimeMillis) }
    single<AudioExportRepo> { AudioExportRepository(get(), get()) }
    single<VideoConverter> { FFmpegVideoConverter(androidContext()) }
    single<VideoOutputStore> { VideoOutputStorage(androidContext().contentResolver, System::currentTimeMillis) }
    single<VideoConvertRepo> { VideoConvertRepository(get(), get()) }
    viewModel { HomeViewModel(androidContext(), get()) }
    viewModel { AudioExportViewModel(get()) }
    viewModel { VideoConvertViewModel(get()) }
}
