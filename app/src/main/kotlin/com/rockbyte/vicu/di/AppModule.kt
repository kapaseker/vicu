package com.rockbyte.vicu.di

import android.app.Application
import com.rockbyte.vicu.page.AudioExportViewModel
import com.rockbyte.vicu.page.HomeViewModel
import com.rockbyte.vicu.repo.MediaRepo
import com.rockbyte.vicu.repo.MediaRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<MediaRepo> { MediaRepository(androidContext().contentResolver) }
    viewModel { HomeViewModel(get()) }

    // 导出页暂无 UI 入口（用户决策）：注册保持架构完整，接入口时零改动
    viewModel { AudioExportViewModel(androidContext() as Application) }
}
