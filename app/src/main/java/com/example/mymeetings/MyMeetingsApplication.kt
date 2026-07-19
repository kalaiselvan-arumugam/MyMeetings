package com.example.mymeetings

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyMeetingsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load native SQLCipher library
        System.loadLibrary("sqlcipher")
    }
}
