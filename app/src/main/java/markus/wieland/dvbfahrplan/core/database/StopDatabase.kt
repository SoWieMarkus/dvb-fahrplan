package markus.wieland.dvbfahrplan.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import markus.wieland.dvbfahrplan.core.geo.GaussKrueger
import markus.wieland.dvbfahrplan.core.model.LatLng
import markus.wieland.dvbfahrplan.core.model.Stop

@Entity(tableName = "stops")
data class StopEntity(
    @PrimaryKey val id: String,
    val name: String,
    val place: String,
    val lat: Double?,
    val lng: Double?,
    @ColumnInfo(name = "last_used") val lastUsed: Long,
    @ColumnInfo(name = "use_count") val useCount: Int,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
) {
    fun toStop() = Stop(id, name, place, if (lat != null && lng != null) LatLng(lat, lng) else null)
}

@Dao
interface StopDao {
    @Query("SELECT * FROM stops WHERE is_favorite = 1 ORDER BY name")
    fun favorites(): Flow<List<StopEntity>>

    @Query("SELECT * FROM stops WHERE is_favorite = 0 ORDER BY last_used DESC LIMIT :limit")
    fun recents(limit: Int): Flow<List<StopEntity>>

    @Query("SELECT id FROM stops WHERE is_favorite = 1")
    fun favoriteIds(): Flow<List<String>>

    @Query("SELECT * FROM stops WHERE id = :id")
    suspend fun get(id: String): StopEntity?

    @Upsert
    suspend fun upsert(entity: StopEntity)

    @Transaction
    suspend fun markUsed(stop: Stop, now: Long) {
        val existing = get(stop.id)
        upsert(
            StopEntity(
                id = stop.id,
                name = stop.name,
                place = stop.place,
                lat = stop.location?.lat ?: existing?.lat,
                lng = stop.location?.lng ?: existing?.lng,
                lastUsed = now,
                useCount = (existing?.useCount ?: 0) + 1,
                isFavorite = existing?.isFavorite ?: false,
            ),
        )
    }

    @Transaction
    suspend fun toggleFavorite(stop: Stop) {
        val existing = get(stop.id)
        upsert(
            existing?.copy(isFavorite = !existing.isFavorite) ?: StopEntity(
                id = stop.id,
                name = stop.name,
                place = stop.place,
                lat = stop.location?.lat,
                lng = stop.location?.lng,
                lastUsed = 0,
                useCount = 0,
                isFavorite = true,
            ),
        )
    }
}

@Database(
    entities = [StopEntity::class, SavedRouteEntity::class],
    version = 2,
    // v2: saved routes. A new table only, so Room can generate the migration from the exported schemas.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class StopDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao
    abstract fun savedRouteDao(): SavedRouteDao

    companion object {
        private const val NAME = "dvb.db"

        /** Database of app version 1.x (table `Point`, recently searched stations). */
        private const val LEGACY_NAME = "school_database"

        fun build(context: Context): StopDatabase =
            Room.databaseBuilder(context, StopDatabase::class.java, NAME)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        importLegacyHistory(context, db)
                    }
                })
                .build()

        /** Copies the search history of the old Java app once, then deletes the old database. */
        private fun importLegacyHistory(context: Context, target: SupportSQLiteDatabase) {
            val legacyFile = context.getDatabasePath(LEGACY_NAME)
            if (!legacyFile.exists()) return
            runCatching {
                SQLiteDatabase.openDatabase(legacyFile.path, null, SQLiteDatabase.OPEN_READONLY).use { legacy ->
                    legacy.rawQuery("SELECT id, place, name, latitude, longitude, latestUse FROM Point", null).use { c ->
                        while (c.moveToNext()) {
                            // The old app stored the GK northing in "longitude" and the easting in "latitude".
                            val location = GaussKrueger.toWgs84(right = c.getLong(3), high = c.getLong(4))
                            val values = ContentValues().apply {
                                put("id", c.getString(0))
                                put("place", c.getString(1).orEmpty())
                                put("name", c.getString(2).orEmpty())
                                put("lat", location?.lat)
                                put("lng", location?.lng)
                                put("last_used", c.getLong(5))
                                put("use_count", 1)
                                put("is_favorite", 0)
                            }
                            target.insert("stops", SQLiteDatabase.CONFLICT_REPLACE, values)
                        }
                    }
                }
            }
            context.deleteDatabase(LEGACY_NAME)
        }
    }
}
