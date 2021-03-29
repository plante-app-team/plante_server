package vegancheckteam.untitled_vegan_app_server.model

enum class UserRightsGroup(val groupName: String) {
    NORMAL("normal"),
    MODERATOR("moderator");
    companion object {
        fun fromStringName(str: String): UserRightsGroup? {
            for (group in values()) {
                if (group.groupName == str.toLowerCase()) {
                    return group;
                }
            }
            return null
        }
    }
}
