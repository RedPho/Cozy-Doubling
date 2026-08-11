package com.grepho.cozydoubling.features.room

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object LocalTaskDataSource {
    private const val PREFS_NAME = "cozy_doubling_tasks"
    private const val KEY_UNFINISHED_TASKS = "unfinished_tasks"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveUnfinishedTasks(tasks: List<FocusTask>) {
        val unfinished = tasks.filter { !it.isCompleted }
        try {
            val json = Json.encodeToString(unfinished)
            prefs?.edit()?.putString(KEY_UNFINISHED_TASKS, json)?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadUnfinishedTasks(): List<FocusTask> {
        val json = prefs?.getString(KEY_UNFINISHED_TASKS, null) ?: return emptyList()
        return try {
            Json.decodeFromString<List<FocusTask>>(json)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Wipes all local task data. Call this on logout or account deletion.
     */
    fun clear() {
        try {
            prefs?.edit()?.remove(KEY_UNFINISHED_TASKS)?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
