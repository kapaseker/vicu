package com.rockbyte.vicu.di

import android.app.Application
import android.content.Context
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class AppModuleTest {

    @Test
    fun appModuleVerifies() {
        // androidContext() 来源（VicuApplication）在单测环境由 extraTypes 声明
        appModule.verify(extraTypes = listOf(Application::class, Context::class))
    }
}
