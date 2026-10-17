package com.physcard.physics

import android.app.Application
import com.physcard.physics.di.AppContainer

class PhysicsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
