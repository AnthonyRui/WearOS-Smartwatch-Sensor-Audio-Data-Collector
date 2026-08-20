package com.example.collectdata.health

import android.app.Activity
import android.content.Context
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.permission.Permission
import java.time.Instant

/** Result of fetching one [HealthMetric]: either rows, or a human-readable [error]. */
data class MetricReadResult(val metric: HealthMetric, val data: MetricRows?, val error: String?)

/** Thin wrapper around [HealthDataStore] that requests permissions once and reads every metric in [ALL_HEALTH_METRICS]. */
class HealthDataRepository(context: Context) {
    private val store: HealthDataStore = HealthDataService.getStore(context.applicationContext)

    private val requiredPermissions: Set<Permission> = ALL_HEALTH_METRICS.map { it.permission }.toSet()

    /**
     * Requests any of [requiredPermissions] not yet granted. May throw
     * [com.samsung.android.sdk.health.data.error.ResolvablePlatformException] if Samsung Health
     * needs to be installed/updated (call `.resolve(activity)` on it), or other
     * [com.samsung.android.sdk.health.data.error.HealthDataException] subclasses on failure.
     */
    suspend fun ensurePermissions(activity: Activity): Set<Permission> {
        val granted = store.getGrantedPermissions(requiredPermissions)
        if (granted.containsAll(requiredPermissions)) return granted
        return store.requestPermissions(requiredPermissions, activity)
    }

    /** Reads every metric over [start, end]; a failure in one metric doesn't block the others. */
    suspend fun fetchSession(start: Instant, end: Instant): List<MetricReadResult> {
        val granted = store.getGrantedPermissions(requiredPermissions)
        return ALL_HEALTH_METRICS.map { metric ->
            if (metric.permission !in granted) {
                MetricReadResult(metric, null, "permission not granted")
            } else {
                try {
                    MetricReadResult(metric, metric.read(store, start, end), null)
                } catch (e: Exception) {
                    MetricReadResult(metric, null, e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }
}
