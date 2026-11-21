package markus.wieland.dvbfahrplan.core.model

/**
 * Means of transport as the VVO API reports it (`Mot`, `Mot.Type`, prefix of a MapData string).
 * The API uses several spellings for the same thing, [fromApi] folds them together.
 */
enum class Mode {
    TRAM,
    BUS,
    REGIONAL_BUS,
    SUBURBAN,
    TRAIN,
    FERRY,
    CABLEWAY,
    ON_DEMAND,
    FOOTPATH,
    STAIRS,
    WAIT,
    STAY_IN_VEHICLE,
    UNKNOWN;

    /** Something you ride in (as opposed to walking or waiting between two rides). */
    val isTransit: Boolean
        get() = this !in setOf(FOOTPATH, STAIRS, WAIT, STAY_IN_VEHICLE)

    companion object {
        fun fromApi(value: String?): Mode = when (value?.lowercase()) {
            "tram" -> TRAM
            "bus", "citybus" -> BUS
            "intercitybus", "plusbus" -> REGIONAL_BUS
            "suburbanrailway", "rapidtransit" -> SUBURBAN
            "train" -> TRAIN
            "ferry" -> FERRY
            "cableway", "lift", "overheadrailway" -> CABLEWAY
            "alita", "taxi", "hailedsharedtaxi" -> ON_DEMAND
            "footpath" -> FOOTPATH
            "mobilitystairsup", "mobilitystairsdown", "mobilityescalatorup", "mobilityescalatordown",
            "mobilityelevatorup", "mobilityelevatordown" -> STAIRS
            "stayforconnection", "waiting" -> WAIT
            "stayinvehicle" -> STAY_IN_VEHICLE
            else -> UNKNOWN
        }
    }
}
