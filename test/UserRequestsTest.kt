package vegancheckteam.plante_server

import vegancheckteam.plante_server.cmds.MAX_QUIZ_ANSWERS_COUNT
import java.util.*
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.get
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class UserRequestsTest {
    @Test
    fun googleRegisterUpdateGetUser() {
        withPlanteTestApplication {
            val response = get("/login_or_register_user/", queryParams = mapOf(
                "deviceId" to "123",
                "googleIdToken" to "${UUID.randomUUID()}")).response
            assertEquals(200, response.status()?.value)

            var map = response.jsonMap()
            val id = map["user_id"] as String
            val clientToken = map["client_token"] as String
            assertFalse(id.isEmpty())
            assertFalse(clientToken.isEmpty())

            map = authedGet(clientToken, "/update_user_data/?name=Bob").jsonMap()
            assertEquals(map["result"], "ok")

            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals(map["user_id"], id)
            assertEquals(map["name"], "Bob")
            assertNull(map["apple_id"])
            assertNotNull(map["google_id"])
        }
    }

    @Test
    fun googleRegisterFailGoogleAuthFail() {
        withPlanteTestApplication {
            val map = get("/login_or_register_user/", queryParams = mapOf(
                "deviceId" to "123",
                "googleIdToken" to "GOOGLE_AUTH_FAIL_FOR_TESTING")).jsonMap()
            assertEquals("google_auth_failed", map["error"])
        }
    }

    @Test
    fun googleRegisterFailEmailNotVerified() {
        withPlanteTestApplication {
            val map = get("/login_or_register_user/?deviceId=123&googleIdToken=GOOGLE_AUTH_EMAIL_NOT_VERIFIED").jsonMap()
            assertEquals("google_email_not_verified", map["error"])
        }
    }

    @Test
    fun googleCanLoginSecondTime() {
        withPlanteTestApplication {
            val googleId = UUID.randomUUID()
            var map = get("/login_or_register_user/?deviceId=1&googleIdToken=$googleId").jsonMap()
            val id1 = map["user_id"] as String
            val clientToken1 = map["client_token"] as String

            map = get("/login_or_register_user/?deviceId=2&googleIdToken=$googleId").jsonMap()
            val id2 = map["user_id"] as String
            val clientToken2 = map["client_token"] as String

            assertEquals(id1, id2)
            assertNotEquals(clientToken1, clientToken2)

            // Both token expected to be valid

            val map1 = authedGet(clientToken1, "/user_data/").jsonMap()
            val map2 = authedGet(clientToken2, "/user_data/").jsonMap()
            assertEquals(map1, map2)
            assertEquals(id1, map1["user_id"])
        }
    }

    @Test
    fun appleRegisterUpdateGetUser() {
        withPlanteTestApplication {
            val response = get("/login_or_register_user/?deviceId=123&appleAuthorizationCode=${UUID.randomUUID()}").response
            assertEquals(200, response.status()?.value)

            var map = response.jsonMap()
            val id = map["user_id"] as String
            val clientToken = map["client_token"] as String
            assertFalse(id.isEmpty())
            assertFalse(clientToken.isEmpty())

            map = authedGet(clientToken, "/update_user_data/?name=Bob").jsonMap()
            assertEquals(map["result"], "ok")

            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals(map["user_id"], id)
            assertEquals(map["name"], "Bob")
            assertNotNull(map["apple_id"])
            assertNull(map["google_id"])
        }
    }

    @Test
    fun appleCanLoginSecondTime() {
        withPlanteTestApplication {
            val appleId = UUID.randomUUID()
            var map = get("/login_or_register_user/?deviceId=1&appleAuthorizationCode=$appleId").jsonMap()
            val id1 = map["user_id"] as String
            val clientToken1 = map["client_token"] as String

            map = get("/login_or_register_user/?deviceId=2&appleAuthorizationCode=$appleId").jsonMap()
            val id2 = map["user_id"] as String
            val clientToken2 = map["client_token"] as String

            assertEquals(id1, id2)
            assertNotEquals(clientToken1, clientToken2)

            // Both token expected to be valid

            val map1 = authedGet(clientToken1, "/user_data/").jsonMap()
            val map2 = authedGet(clientToken2, "/user_data/").jsonMap()
            assertEquals(map1, map2)
            assertEquals(id1, map1["user_id"])
        }
    }

    @Test
    fun unauthorizedUpdate() {
        withPlanteTestApplication {
            var response = get("/login_or_register_user/?deviceId=123&googleIdToken=${UUID.randomUUID()}").response
            assertEquals(200, response.status()?.value)

            // NOTE: token is not passed
            response = get("/update_user_data/?name=Bob", clientToken = null).response
            assertNull(response.content)
            assertEquals(401, response.status()?.value)
        }
    }

    @Test
    fun signOutAll() {
        withPlanteTestApplication {
            val googleId = UUID.randomUUID()
            var response = get("/login_or_register_user/?deviceId=123&googleIdToken=$googleId").response
            assertEquals(200, response.status()?.value)

            var map = response.jsonMap()
            val clientToken = map["client_token"] as String

            map = authedGet(clientToken, "/sign_out_all/").jsonMap()
            assertEquals(map["result"], "ok")

            // NOTE: token is passed but auth is still expected to fail
            response = authedGet(clientToken, "/update_user_data/?name=Bob").response
            assertNull(response.content)
            assertEquals(401, response.status()?.value)

            // Login again
            map = get("/login_or_register_user/?deviceId=2&googleIdToken=$googleId").jsonMap()
            val clientToken2 = map["client_token"] as String

            response = authedGet(clientToken2, "/update_user_data/?name=Bob").response
            assertEquals(200, response.status()?.value)
        }
    }

    @Test
    fun allFieldsUpdates() {
        withPlanteTestApplication {
            val response = get("/login_or_register_user/?deviceId=123&googleIdToken=${UUID.randomUUID()}").response
            assertEquals(200, response.status()?.value)

            var map = response.jsonMap()
            val clientToken = map["client_token"] as String

            authedGet(clientToken, "/update_user_data/?name=Bob")
            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("Bob", map["name"])
            assertEquals(null, map["self_description"])
            assertEquals(null, map["gender"])
            assertEquals(null, map["birthday"])
            assertEquals(null, map["langs_prioritized"])

            val selfDescr = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
            authedGet(clientToken, "/update_user_data/", mapOf(
                "selfDescription" to selfDescr,
            ))
            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("Bob", map["name"])
            assertEquals(selfDescr, map["self_description"])
            assertEquals(null, map["gender"])
            assertEquals(null, map["birthday"])
            assertEquals(null, map["langs_prioritized"])

            authedGet(clientToken, "/update_user_data/?gender=male")
            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("Bob", map["name"])
            assertEquals(selfDescr, map["self_description"])
            assertEquals("male", map["gender"])
            assertEquals(null, map["birthday"])
            assertEquals(null, map["langs_prioritized"])

            authedGet(clientToken, "/update_user_data/?birthday=20.07.1993")
            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("Bob", map["name"])
            assertEquals(selfDescr, map["self_description"])
            assertEquals("male", map["gender"])
            assertEquals("20.07.1993", map["birthday"])
            assertEquals(null, map["langs_prioritized"])

            authedGet(clientToken, "/update_user_data/?langsPrioritized=en&langsPrioritized=ru")
            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("Bob", map["name"])
            assertEquals(selfDescr, map["self_description"])
            assertEquals("male", map["gender"])
            assertEquals("20.07.1993", map["birthday"])
            assertEquals(listOf("en", "ru"), map["langs_prioritized"])
        }
    }

    @Test
    fun `langs_prioritized update order`() {
        withPlanteTestApplication {
            val response = get("/login_or_register_user/?deviceId=123&googleIdToken=${UUID.randomUUID()}").response
            assertEquals(200, response.status()?.value)

            var map = response.jsonMap()
            val clientToken = map["client_token"] as String

            authedGet(clientToken, "/update_user_data/?langsPrioritized=en&langsPrioritized=ru")
            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals(listOf("en", "ru"), map["langs_prioritized"])

            authedGet(clientToken, "/update_user_data/?langsPrioritized=ru&langsPrioritized=en")
            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals(listOf("ru", "en"), map["langs_prioritized"])
        }
    }

    @Test
    fun invalidGenderUpdate() {
        withPlanteTestApplication {
            val response = get("/login_or_register_user/?deviceId=123&googleIdToken=${UUID.randomUUID()}").response
            assertEquals(200, response.status()?.value)

            var map = response.jsonMap()
            val clientToken = map["client_token"] as String

            map = authedGet(clientToken, "/update_user_data/?gender=Bob").jsonMap()
            assertEquals("invalid_gender", map["error"])
            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals(null, map["gender"])
        }
    }

    @Test
    fun invalidBirthday() {
        withPlanteTestApplication {
            val response = get("/login_or_register_user/?deviceId=123&googleIdToken=${UUID.randomUUID()}").response
            assertEquals(200, response.status()?.value)

            var map = response.jsonMap()
            val clientToken = map["client_token"] as String

            map = authedGet(clientToken, "/update_user_data/?birthday=Bob").jsonMap()
            assertEquals("invalid_date", map["error"])
            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals(null, map["birthday"])
        }
    }

    @Test
    fun quiz() {
        withPlanteTestApplication {
            val clientToken = register()

            var map = authedGet(clientToken, "/user_quiz/?question=what&answer=nothing").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/user_quiz/?question=how&answer=noway").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/user_quiz_data/").jsonMap()
            val questions = map["questions"] as List<*>
            val answers = map["answers"] as List<*>
            assertEquals(2, questions.size)
            assertEquals(2, answers.size)
            assertEquals("what", questions[0])
            assertEquals("nothing", answers[0])
            assertEquals("how", questions[1])
            assertEquals("noway", answers[1])
        }
    }

    @Test
    fun `quiz answer overriding`() {
        withPlanteTestApplication {
            val clientToken = register()

            authedGet(clientToken, "/user_quiz/?question=what&answer=nothing").jsonMap()
            var map = authedGet(clientToken, "/user_quiz_data/").jsonMap()
            var questions = map["questions"] as List<*>
            var answers = map["answers"] as List<*>
            assertEquals("what", questions[0])
            assertEquals("nothing", answers[0])
            assertEquals(1, questions.size)
            assertEquals(1, answers.size)

            authedGet(clientToken, "/user_quiz/?question=what&answer=something").jsonMap()
            map = authedGet(clientToken, "/user_quiz_data/").jsonMap()
            questions = map["questions"] as List<*>
            answers = map["answers"] as List<*>
            assertEquals("what", questions[0])
            assertEquals("something", answers[0])
            assertEquals(1, questions.size)
            assertEquals(1, answers.size)
        }
    }

    @Test
    fun `quiz too many answers`() {
        withPlanteTestApplication {
            val clientToken = register()

            for (index in 0..(MAX_QUIZ_ANSWERS_COUNT*2)) {
                val map = authedGet(clientToken, "/user_quiz/?question=what$index&answer=answer$index").jsonMap()
                if (index < MAX_QUIZ_ANSWERS_COUNT) {
                    assertEquals("ok", map["result"], "Index: $index, map: $map")
                } else {
                    assertEquals("too_many_answers", map["error"], "Index: $index, map: $map")
                }
            }

            val map = authedGet(clientToken, "/user_quiz_data/").jsonMap()
            val questions = map["questions"] as List<*>
            val answers = map["answers"] as List<*>
            assertEquals(MAX_QUIZ_ANSWERS_COUNT, questions.size)
            assertEquals(MAX_QUIZ_ANSWERS_COUNT, answers.size)
        }
    }
}
