package vegancheckteam.plante_server.base

import java.time.Instant

/**
 * Returns seconds since epoch, in UTC.
 */
fun now(testingNow: Long? = null, testing: Boolean = false): Long {
    if (testingNow != null && testing) {
        return testingNow
    }
    return Instant.now().epochSecond
}

/**
 * Returns millis since epoch.
 */
fun nowMillis(testingNow: Long? = null, testing: Boolean = false): Long {
    if (testingNow != null && testing) {
        return testingNow
    }
    return Instant.now().toEpochMilli()
}
