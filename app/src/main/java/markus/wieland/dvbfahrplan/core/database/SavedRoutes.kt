package markus.wieland.dvbfahrplan.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import markus.wieland.dvbfahrplan.core.model.LatLng
import markus.wieland.dvbfahrplan.core.model.SavedRoute
import markus.wieland.dvbfahrplan.core.model.Stop

/**
 * A remembered origin → destination pair. Both stops are stored as a snapshot, so a saved route
 * does not depend on the stops still being in the history table.
 */
@Entity(tableName = "saved_routes", primaryKeys = ["origin_id", "destination_id"])
data class SavedRouteEntity(
    @ColumnInfo(name = "origin_id") val originId: String,
    @ColumnInfo(name = "origin_name") val originName: String,
    @ColumnInfo(name = "origin_place") val originPlace: String,
    @ColumnInfo(name = "origin_lat") val originLat: Double?,
    @ColumnInfo(name = "origin_lng") val originLng: Double?,
    @ColumnInfo(name = "destination_id") val destinationId: String,
    @ColumnInfo(name = "destination_name") val destinationName: String,
    @ColumnInfo(name = "destination_place") val destinationPlace: String,
    @ColumnInfo(name = "destination_lat") val destinationLat: Double?,
    @ColumnInfo(name = "destination_lng") val destinationLng: Double?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    fun toSavedRoute() = SavedRoute(
        origin = Stop(originId, originName, originPlace, location(originLat, originLng)),
        destination = Stop(destinationId, destinationName, destinationPlace, location(destinationLat, destinationLng)),
    )

    companion object {
        fun from(origin: Stop, destination: Stop, now: Long) = SavedRouteEntity(
            originId = origin.id,
            originName = origin.name,
            originPlace = origin.place,
            originLat = origin.location?.lat,
            originLng = origin.location?.lng,
            destinationId = destination.id,
            destinationName = destination.name,
            destinationPlace = destination.place,
            destinationLat = destination.location?.lat,
            destinationLng = destination.location?.lng,
            createdAt = now,
        )

        private fun location(lat: Double?, lng: Double?) = if (lat != null && lng != null) LatLng(lat, lng) else null
    }
}

@Dao
interface SavedRouteDao {
    @Query("SELECT * FROM saved_routes ORDER BY created_at")
    fun all(): Flow<List<SavedRouteEntity>>

    @Query("SELECT COUNT(*) FROM saved_routes WHERE origin_id = :originId AND destination_id = :destinationId")
    suspend fun count(originId: String, destinationId: String): Int

    @Upsert
    suspend fun upsert(entity: SavedRouteEntity)

    @Query("DELETE FROM saved_routes WHERE origin_id = :originId AND destination_id = :destinationId")
    suspend fun delete(originId: String, destinationId: String)

    @Transaction
    suspend fun toggle(origin: Stop, destination: Stop, now: Long) {
        if (count(origin.id, destination.id) > 0) delete(origin.id, destination.id)
        else upsert(SavedRouteEntity.from(origin, destination, now))
    }
}
