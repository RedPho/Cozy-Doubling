package com.grepho.cozydoubling

import android.app.Application
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.features.room.LocalTaskDataSource

/**
 * Builds the dependency graph once per process, before any Activity or Service runs.
 *
 * Previously MainActivity did this, so a process started by FCM (CozyFirebaseMessagingService)
 * or by the focus foreground service used a throw-away container without the persisted token,
 * and MainActivity later swapped in a second container with separate repository state.
 */
class CozyDoublingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        LocalTaskDataSource.init(this)
    }
}
