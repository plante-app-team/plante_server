package vegancheckteam.plante_server.model

import java.util.Locale

enum class Gender(
        val genderName: String,
        val persistentCode: Short) {
    MALE("male", 1),
    FEMALE("female", 2);
    companion object {
        fun fromStringName(str: String) = values().find { it.genderName == str.lowercase() }
        fun fromPersistentCode(code: Short) = values().find { it.persistentCode == code }
    }
}
