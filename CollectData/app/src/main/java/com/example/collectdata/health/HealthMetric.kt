package com.example.collectdata.health

import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.InstantTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Flattened CSV rows for a single health metric over a time window. */
data class MetricRows(val header: List<String>, val rows: List<List<String>>)

/** A single Samsung Health data type this app knows how to fetch and flatten into CSV rows. */
interface HealthMetric {
    val fileLabel: String
    val permission: Permission
    suspend fun read(store: HealthDataStore, start: Instant, end: Instant): MetricRows
}

private fun Instant.toLocal(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())

/** Continuous/periodic heart rate readings (requires auto or continuous HR tracking to be on). */
object HeartRateMetric : HealthMetric {
    override val fileLabel = "HeartRate"
    override val permission: Permission = Permission.of(DataTypes.HEART_RATE, AccessType.READ)

    override suspend fun read(store: HealthDataStore, start: Instant, end: Instant): MetricRows {
        val request = DataTypes.HEART_RATE.readDataRequestBuilder
            .setInstantTimeFilter(InstantTimeFilter.of(start, end))
            .build()
        val rows = mutableListOf<List<String>>()
        for (point in store.readData(request).dataList) {
            val series = point.getValueOrDefault(DataType.HeartRateType.SERIES_DATA, emptyList())
            if (series.isEmpty()) {
                rows += listOf(
                    point.startTime.toLocal().toString(),
                    point.endTime?.toLocal().toString(),
                    point.getValueOrDefault(DataType.HeartRateType.HEART_RATE, 0f).toString(),
                    point.getValueOrDefault(DataType.HeartRateType.MIN_HEART_RATE, 0f).toString(),
                    point.getValueOrDefault(DataType.HeartRateType.MAX_HEART_RATE, 0f).toString(),
                )
            } else {
                for (sample in series) {
                    rows += listOf(
                        sample.startTime.toLocal().toString(),
                        sample.endTime.toLocal().toString(),
                        sample.heartRate.toString(),
                        sample.min.toString(),
                        sample.max.toString(),
                    )
                }
            }
        }
        return MetricRows(listOf("startTime", "endTime", "heartRate", "min", "max"), rows)
    }
}

/** Spot/periodic SpO2 (blood oxygen) readings; only present on watches that support it. */
object BloodOxygenMetric : HealthMetric {
    override val fileLabel = "BloodOxygen"
    override val permission: Permission = Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ)

    override suspend fun read(store: HealthDataStore, start: Instant, end: Instant): MetricRows {
        val request = DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
            .setInstantTimeFilter(InstantTimeFilter.of(start, end))
            .build()
        val rows = mutableListOf<List<String>>()
        for (point in store.readData(request).dataList) {
            val series = point.getValueOrDefault(DataType.BloodOxygenType.SERIES_DATA, emptyList())
            if (series.isEmpty()) {
                rows += listOf(
                    point.startTime.toLocal().toString(),
                    point.endTime?.toLocal().toString(),
                    point.getValueOrDefault(DataType.BloodOxygenType.OXYGEN_SATURATION, 0f).toString(),
                    point.getValueOrDefault(DataType.BloodOxygenType.MIN_OXYGEN_SATURATION, 0f).toString(),
                    point.getValueOrDefault(DataType.BloodOxygenType.MAX_OXYGEN_SATURATION, 0f).toString(),
                )
            } else {
                for (sample in series) {
                    rows += listOf(
                        sample.startTime.toLocal().toString(),
                        sample.endTime.toLocal().toString(),
                        sample.oxygenSaturation.toString(),
                        sample.min.toString(),
                        sample.max.toString(),
                    )
                }
            }
        }
        return MetricRows(listOf("startTime", "endTime", "oxygenSaturation", "min", "max"), rows)
    }
}

/** Skin temperature series; only present on watches with a temperature sensor (e.g. Galaxy Watch 5+). */
object SkinTemperatureMetric : HealthMetric {
    override val fileLabel = "SkinTemperature"
    override val permission: Permission = Permission.of(DataTypes.SKIN_TEMPERATURE, AccessType.READ)

    override suspend fun read(store: HealthDataStore, start: Instant, end: Instant): MetricRows {
        val request = DataTypes.SKIN_TEMPERATURE.readDataRequestBuilder
            .setInstantTimeFilter(InstantTimeFilter.of(start, end))
            .build()
        val rows = mutableListOf<List<String>>()
        for (point in store.readData(request).dataList) {
            val series = point.getValueOrDefault(DataType.SkinTemperatureType.SERIES_DATA, emptyList())
            if (series.isEmpty()) {
                rows += listOf(
                    point.startTime.toLocal().toString(),
                    point.endTime?.toLocal().toString(),
                    point.getValueOrDefault(DataType.SkinTemperatureType.SKIN_TEMPERATURE, 0f).toString(),
                    point.getValueOrDefault(DataType.SkinTemperatureType.MIN_SKIN_TEMPERATURE, 0f).toString(),
                    point.getValueOrDefault(DataType.SkinTemperatureType.MAX_SKIN_TEMPERATURE, 0f).toString(),
                )
            } else {
                for (sample in series) {
                    rows += listOf(
                        sample.startTime.toLocal().toString(),
                        sample.endTime.toLocal().toString(),
                        sample.skinTemperature.toString(),
                        sample.min.toString(),
                        sample.max.toString(),
                    )
                }
            }
        }
        return MetricRows(listOf("startTime", "endTime", "skinTemperature", "min", "max"), rows)
    }
}

/** Blood pressure readings; typically manually entered (Samsung Health app or a paired BP cuff). */
object BloodPressureMetric : HealthMetric {
    override val fileLabel = "BloodPressure"
    override val permission: Permission = Permission.of(DataTypes.BLOOD_PRESSURE, AccessType.READ)

    override suspend fun read(store: HealthDataStore, start: Instant, end: Instant): MetricRows {
        val request = DataTypes.BLOOD_PRESSURE.readDataRequestBuilder
            .setInstantTimeFilter(InstantTimeFilter.of(start, end))
            .build()
        val rows = store.readData(request).dataList.map { point ->
            listOf(
                point.startTime.toLocal().toString(),
                point.endTime?.toLocal().toString(),
                point.getValueOrDefault(DataType.BloodPressureType.SYSTOLIC, 0f).toString(),
                point.getValueOrDefault(DataType.BloodPressureType.DIASTOLIC, 0f).toString(),
                point.getValueOrDefault(DataType.BloodPressureType.MEAN, 0f).toString(),
                point.getValueOrDefault(DataType.BloodPressureType.PULSE_RATE, 0).toString(),
                point.getValueOrDefault(DataType.BloodPressureType.MEDICATION_TAKEN, false).toString(),
            )
        }
        return MetricRows(
            listOf("startTime", "endTime", "systolic", "diastolic", "mean", "pulseRate", "medicationTaken"),
            rows,
        )
    }
}

/** Body composition (scale) readings: weight, body fat, muscle mass, etc. Typically manually entered. */
object BodyCompositionMetric : HealthMetric {
    override val fileLabel = "BodyComposition"
    override val permission: Permission = Permission.of(DataTypes.BODY_COMPOSITION, AccessType.READ)

    override suspend fun read(store: HealthDataStore, start: Instant, end: Instant): MetricRows {
        val request = DataTypes.BODY_COMPOSITION.readDataRequestBuilder
            .setInstantTimeFilter(InstantTimeFilter.of(start, end))
            .build()
        val rows = store.readData(request).dataList.map { point ->
            listOf(
                point.startTime.toLocal().toString(),
                point.endTime?.toLocal().toString(),
                point.getValueOrDefault(DataType.BodyCompositionType.WEIGHT, 0f).toString(),
                point.getValueOrDefault(DataType.BodyCompositionType.HEIGHT, 0f).toString(),
                point.getValueOrDefault(DataType.BodyCompositionType.BODY_FAT, 0f).toString(),
                point.getValueOrDefault(DataType.BodyCompositionType.SKELETAL_MUSCLE, 0f).toString(),
                point.getValueOrDefault(DataType.BodyCompositionType.MUSCLE_MASS, 0f).toString(),
                point.getValueOrDefault(DataType.BodyCompositionType.BASAL_METABOLIC_RATE, 0).toString(),
                point.getValueOrDefault(DataType.BodyCompositionType.BODY_FAT_MASS, 0f).toString(),
                point.getValueOrDefault(DataType.BodyCompositionType.FAT_FREE_MASS, 0f).toString(),
                point.getValueOrDefault(DataType.BodyCompositionType.FAT_FREE, 0f).toString(),
                point.getValueOrDefault(DataType.BodyCompositionType.SKELETAL_MUSCLE_MASS, 0f).toString(),
                point.getValueOrDefault(DataType.BodyCompositionType.TOTAL_BODY_WATER, 0f).toString(),
                point.getValueOrDefault(DataType.BodyCompositionType.BODY_MASS_INDEX, 0f).toString(),
            )
        }
        return MetricRows(
            listOf(
                "startTime", "endTime", "weight", "height", "bodyFat", "skeletalMuscle", "muscleMass",
                "basalMetabolicRate", "bodyFatMass", "fatFreeMass", "fatFree", "skeletalMuscleMass",
                "totalBodyWater", "bodyMassIndex",
            ),
            rows,
        )
    }
}

/** Logged meals/food entries with calorie and macro/micronutrient breakdown. Manually entered by the user. */
object NutritionMetric : HealthMetric {
    override val fileLabel = "Nutrition"
    override val permission: Permission = Permission.of(DataTypes.NUTRITION, AccessType.READ)

    override suspend fun read(store: HealthDataStore, start: Instant, end: Instant): MetricRows {
        val request = DataTypes.NUTRITION.readDataRequestBuilder
            .setInstantTimeFilter(InstantTimeFilter.of(start, end))
            .build()
        val rows = store.readData(request).dataList.map { point ->
            listOf(
                point.startTime.toLocal().toString(),
                point.endTime?.toLocal().toString(),
                point.getValueOrDefault(DataType.NutritionType.MEAL_TYPE, DataType.NutritionType.MealType.UNDEFINED).name,
                point.getValueOrDefault(DataType.NutritionType.TITLE, ""),
                point.getValueOrDefault(DataType.NutritionType.CALORIES, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.TOTAL_FAT, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.SATURATED_FAT, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.POLYSATURATED_FAT, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.MONOSATURATED_FAT, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.TRANS_FAT, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.CARBOHYDRATE, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.DIETARY_FIBER, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.SUGAR, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.PROTEIN, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.CHOLESTEROL, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.SODIUM, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.POTASSIUM, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.VITAMIN_A, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.VITAMIN_C, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.CALCIUM, 0f).toString(),
                point.getValueOrDefault(DataType.NutritionType.IRON, 0f).toString(),
            )
        }
        return MetricRows(
            listOf(
                "startTime", "endTime", "mealType", "title", "calories", "totalFat", "saturatedFat",
                "polysaturatedFat", "monosaturatedFat", "transFat", "carbohydrate", "dietaryFiber",
                "sugar", "protein", "cholesterol", "sodium", "potassium", "vitaminA", "vitaminC",
                "calcium", "iron",
            ),
            rows,
        )
    }
}

/**
 * Exercise sessions overlapping the window. Only populated if the workout was recorded through
 * Samsung Health / the watch's Exercise app (a separate thing from this project's custom sensor
 * collector) or auto-detected by the watch.
 */
object ExerciseMetric : HealthMetric {
    override val fileLabel = "Exercise"
    override val permission: Permission = Permission.of(DataTypes.EXERCISE, AccessType.READ)

    override suspend fun read(store: HealthDataStore, start: Instant, end: Instant): MetricRows {
        val request = DataTypes.EXERCISE.readDataRequestBuilder
            .setInstantTimeFilter(InstantTimeFilter.of(start, end))
            .build()
        val rows = mutableListOf<List<String>>()
        for (point in store.readData(request).dataList) {
            val sessions = point.getValueOrDefault(DataType.ExerciseType.SESSIONS, emptyList())
            for (session in sessions) {
                rows += listOf(
                    session.startTime.toLocal().toString(),
                    session.endTime.toLocal().toString(),
                    session.duration.seconds.toString(),
                    session.exerciseType?.name.orEmpty(),
                    session.customTitle.orEmpty(),
                    session.calories.toString(),
                    (session.distance ?: 0f).toString(),
                    (session.meanHeartRate ?: 0f).toString(),
                    (session.maxHeartRate ?: 0f).toString(),
                    (session.minHeartRate ?: 0f).toString(),
                )
            }
        }
        return MetricRows(
            listOf(
                "startTime", "endTime", "durationSeconds", "exerciseType", "customTitle",
                "calories", "distance", "meanHeartRate", "maxHeartRate", "minHeartRate",
            ),
            rows,
        )
    }
}

/** Total step count for the window. Steps can only be read as an aggregate, not per-sample. */
object StepsMetric : HealthMetric {
    override val fileLabel = "Steps"
    override val permission: Permission = Permission.of(DataTypes.STEPS, AccessType.READ)

    override suspend fun read(store: HealthDataStore, start: Instant, end: Instant): MetricRows {
        val request = DataType.StepsType.TOTAL.requestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(start.toLocal(), end.toLocal()))
            .build()
        val rows = store.aggregateData(request).dataList.map { agg ->
            listOf(agg.startTime.toLocal().toString(), agg.endTime.toLocal().toString(), agg.getValueOrDefault(0L).toString())
        }
        return MetricRows(listOf("startTime", "endTime", "totalSteps"), rows)
    }
}

/** Total floors climbed for the window (aggregate only, same limitation as steps). */
object FloorsClimbedMetric : HealthMetric {
    override val fileLabel = "FloorsClimbed"
    override val permission: Permission = Permission.of(DataTypes.FLOORS_CLIMBED, AccessType.READ)

    override suspend fun read(store: HealthDataStore, start: Instant, end: Instant): MetricRows {
        val request = DataType.FloorsClimbedType.TOTAL.requestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(start.toLocal(), end.toLocal()))
            .build()
        val rows = store.aggregateData(request).dataList.map { agg ->
            listOf(agg.startTime.toLocal().toString(), agg.endTime.toLocal().toString(), agg.getValueOrDefault(0f).toString())
        }
        return MetricRows(listOf("startTime", "endTime", "totalFloors"), rows)
    }
}

/** Daily activity summary totals (calories, active time, distance) clipped to the window. */
object ActivitySummaryMetric : HealthMetric {
    override val fileLabel = "ActivitySummary"
    override val permission: Permission = Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ)

    override suspend fun read(store: HealthDataStore, start: Instant, end: Instant): MetricRows {
        val filter = LocalTimeFilter.of(start.toLocal(), end.toLocal())

        val activeCaloriesReq = DataType.ActivitySummaryType.TOTAL_ACTIVE_CALORIES_BURNED.requestBuilder
            .setLocalTimeFilter(filter).build()
        val activeCalories = store.aggregateData(activeCaloriesReq).dataList.firstOrNull()?.getValueOrDefault(0f) ?: 0f

        val totalCaloriesReq = DataType.ActivitySummaryType.TOTAL_CALORIES_BURNED.requestBuilder
            .setLocalTimeFilter(filter).build()
        val totalCalories = store.aggregateData(totalCaloriesReq).dataList.firstOrNull()?.getValueOrDefault(0f) ?: 0f

        val activeTimeReq = DataType.ActivitySummaryType.TOTAL_ACTIVE_TIME.requestBuilder
            .setLocalTimeFilter(filter).build()
        val activeTime = store.aggregateData(activeTimeReq).dataList.firstOrNull()?.getValueOrDefault(Duration.ZERO)
            ?: Duration.ZERO

        val distanceReq = DataType.ActivitySummaryType.TOTAL_DISTANCE.requestBuilder
            .setLocalTimeFilter(filter).build()
        val distance = store.aggregateData(distanceReq).dataList.firstOrNull()?.getValueOrDefault(0f) ?: 0f

        val row = listOf(
            start.toLocal().toString(),
            end.toLocal().toString(),
            activeCalories.toString(),
            totalCalories.toString(),
            activeTime.seconds.toString(),
            distance.toString(),
        )
        return MetricRows(
            listOf("startTime", "endTime", "activeCaloriesBurned", "totalCaloriesBurned", "activeTimeSeconds", "totalDistance"),
            listOf(row),
        )
    }
}

val ALL_HEALTH_METRICS: List<HealthMetric> = listOf(
    HeartRateMetric,
    BloodOxygenMetric,
    SkinTemperatureMetric,
    BloodPressureMetric,
    BodyCompositionMetric,
    NutritionMetric,
    ExerciseMetric,
    StepsMetric,
    FloorsClimbedMetric,
    ActivitySummaryMetric,
)
