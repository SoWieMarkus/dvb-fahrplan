package markus.wieland.dvbfahrplan.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import markus.wieland.dvbfahrplan.core.database.SavedRouteDao
import markus.wieland.dvbfahrplan.core.database.StopDao
import markus.wieland.dvbfahrplan.core.database.StopDatabase
import markus.wieland.dvbfahrplan.core.network.VvoApi
import okhttp3.OkHttpClient
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun vvoApi(client: OkHttpClient): VvoApi = VvoApi(client)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): StopDatabase = StopDatabase.build(context)

    @Provides
    fun stopDao(db: StopDatabase): StopDao = db.stopDao()

    @Provides
    fun savedRouteDao(db: StopDatabase): SavedRouteDao = db.savedRouteDao()

    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}
