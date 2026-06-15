package com.circuitstitch.toys

import android.app.Application
import android.content.Context
import timber.log.Timber.DebugTree
import timber.log.Timber.Forest.plant


class AnimalSpinApp : Application() {

    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            plant(DebugTree())
        }
    }

    companion object {
        private var instance: AnimalSpinApp? = null

        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }

}
