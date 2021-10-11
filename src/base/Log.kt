package vegancheckteam.plante_server.base

import org.slf4j.Marker
import org.slf4j.helpers.BasicMarkerFactory
import vegancheckteam.plante_server.GlobalStorage

object Log {
    private val markersFactory = BasicMarkerFactory()

    private fun markerFor(tag: String): Marker {
        return markersFactory.getMarker(tag)
    }

    fun v(tag: String, msg: String, e: Throwable? = null) {
        GlobalStorage.logger.trace(markerFor(tag), msg, e)
    }

    fun d(tag: String, msg: String, e: Throwable? = null) {
        GlobalStorage.logger.debug(markerFor(tag), msg, e)
    }

    fun i(tag: String, msg: String, e: Throwable? = null) {
        GlobalStorage.logger.info(markerFor(tag), msg, e)
    }

    fun w(tag: String, msg: String, e: Throwable? = null) {
        GlobalStorage.logger.warn(markerFor(tag), msg, e)
    }

    fun e(tag: String, msg: String, e: Throwable? = null) {
        GlobalStorage.logger.error(markerFor(tag), msg, e)
    }
}
