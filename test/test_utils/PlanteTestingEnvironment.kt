package vegancheckteam.plante_server.test_utils

import io.ktor.server.engine.ApplicationEngineEnvironment
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import vegancheckteam.plante_server.module
import vegancheckteam.plante_server.workers.ShopsValidationWorker

private fun <R> withPlanteApplication(
    environment: ApplicationEngineEnvironment = createTestEnvironment(),
    configure: TestApplicationEngine.Configuration.() -> Unit = {},
    test: TestApplicationEngine.() -> R
): R {
    val engine = TestApplicationEngine(environment, configure)
    engine.start()
    try {
        return engine.test()
    } finally {
        engine.stop(0L, 0L)
        ShopsValidationWorker.stop()
    }
}


/**
 * Start test application engine, pass it to [test] function and stop it
 */
public fun <R> withPlanteTestApplication(test: TestApplicationEngine.() -> R): R {
    return withPlanteApplication(createTestEnvironment()) {
        application.module(testing = true)
        test()
    }
}
