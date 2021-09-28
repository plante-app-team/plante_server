package vegancheckteam.plante_server.model

import java.util.Locale

enum class UserRightsGroup(
        val groupName: String,
        val persistentCode: Short) {
    NORMAL("normal", 1),
    TESTER("tester", 2),
    CONTENT_MODERATOR("content_moderator", 3),
    EVERYTHING_MODERATOR("everything_moderator", 4),
    ADMINISTRATOR("administrator", 5);
    companion object {
        fun fromStringName(str: String) = values().find { it.groupName == str.lowercase() }
        fun fromPersistentCode(code: Short) = values().find { it.persistentCode == code }
    }
}
