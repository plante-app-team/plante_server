package vegancheckteam.untitled_vegan_app_server.model

enum class UserRightsGroup(
        val groupName: String,
        val persistentCode: Short) {
    NORMAL("normal", 1),
    MODERATOR("moderator", 2);
    companion object {
        fun fromStringName(str: String) = values().find { it.groupName == str.toLowerCase() }
        fun fromPersistentCode(code: Short) = values().find { it.persistentCode == code }
    }
}
