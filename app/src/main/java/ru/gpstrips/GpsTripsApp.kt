package ru.gpstrips

import android.app.Application
import com.yandex.mapkit.MapKitFactory

/**
 * Инициализация MapKit. Ключ приходит из MAPKITSDK при сборке
 * (в исходниках и репозитории его нет).
 */
class GpsTripsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.MAPKIT_API_KEY.isNotBlank()) {
            MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY)
            MapKitFactory.initialize(this)
        }
    }
}
