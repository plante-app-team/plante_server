package vegancheckteam.plante_server.base

import kotlin.math.absoluteValue
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or

class AreaTooBigException(s: String) : IllegalArgumentException(s)

@Throws(AreaTooBigException::class)
fun <T : Double?> geoSelect(
    west: Double,
    east: Double,
    north: Double,
    south: Double,
    lat: Column<T>,
    lon: Column<T>,
    maxSize: Double,
): Op<Boolean> {
    val edgeBorders = east < west

    val width = if (!edgeBorders) {
        (west - east).absoluteValue
    } else {
        (180 - west) + (180 + east)
    }
    val height = (north - south).absoluteValue
    if (maxSize < width || maxSize < height) {
        throw AreaTooBigException("Too big area are requested: $west $east $north $south ($width $height)")
    }

    val withinLat = (lat greaterEq south) and (lat lessEq north)
    val withinLon = if (!edgeBorders) {
        (lon greaterEq west) and (lon lessEq east)
    } else {
        (lon greaterEq west) or (lon lessEq east)
    }

    return withinLon and withinLat
}

/// Note: it's very approximate since Earth is all round and complex.
fun kmToGrad(km: Double): Double {
    return km * 1 / 111
}
