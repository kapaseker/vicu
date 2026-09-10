package com.rockbyte.vicu.di

import com.rockbyte.vicu.page.AudioExportViewModel
import com.rockbyte.vicu.page.HomeViewModel
import com.rockbyte.vicu.repo.AudioExportRepo
import com.rockbyte.vicu.repo.AudioExportRepository
import com.rockbyte.vicu.repo.MediaRepo
import com.rockbyte.vicu.repo.MediaRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<MediaRepo> { MediaRepository(androidContext().contentResolver) }
    single<AudioExportRepo> { AudioExportRepository(androidContext()) }
    viewModel { HomeViewModel(get()) }
    viewModel { AudioExportViewModel(get()) }
}
