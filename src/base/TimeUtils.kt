package vegancheckteam.plante_server.base

import java.time.Instant

fun now(testingNow: Long? = null, testing: Boolean = false): Long {
    if (testingNow != null && testing) {
        return testingNow
    }
    return Instant.now().epochSecond
}
