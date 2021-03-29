package vegancheckteam.untitled_vegan_app_server.model

enum class Gender(val genderName: String) {
    MALE("male"),
    FEMALE("female");
    companion object {
        fun fromStringName(str: String): Gender? {
            for (gender in values()) {
                if (gender.genderName == str.toLowerCase()) {
                    return gender;
                }
            }
            return null
        }
    }
}
