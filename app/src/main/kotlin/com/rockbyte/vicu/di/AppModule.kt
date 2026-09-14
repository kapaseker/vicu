package com.rockbyte.vicu.di

import com.rockbyte.vicu.page.AudioExportViewModel
import com.rockbyte.vicu.page.HomeViewModel
import com.rockbyte.vicu.repo.AudioExportRepo
import com.rockbyte.vicu.repo.AudioExportRepository
import com.rockbyte.vicu.repo.AudioEncoder
import com.rockbyte.vicu.repo.FFmpegAudioEncoder
import com.rockbyte.vicu.repo.AudioOutputStore
import com.rockbyte.vicu.repo.AudioOutputStorage
import com.rockbyte.vicu.repo.MediaRepo
import com.rockbyte.vicu.repo.MediaRepository
import com.rockbyte.vicu.repo.MediaLibraryStore
import com.rockbyte.vicu.repo.MediaLibraryStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<MediaLibraryStore> { MediaLibraryStorage(androidContext()) }
    single<MediaRepo> { MediaRepository(get()) }
    single<AudioEncoder> { FFmpegAudioEncoder(androidContext()) }
    single<AudioOutputStore> { AudioOutputStorage(androidContext().contentResolver, System::currentTimeMillis) }
    single<AudioExportRepo> { AudioExportRepository(get(), get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { AudioExportViewModel(get()) }
}
