package crow.teomant.hebrewwordstelegrambot.documents.user

import lombok.EqualsAndHashCode
import lombok.ToString
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "hebrew.user")
@ToString(of = ["id", "chatId"])
@EqualsAndHashCode(of = ["id"])
data class User(
        @Id
        val id: ObjectId,
        @Indexed(unique = true)
        val telegramId: String,
        val words: MutableSet<String>,
        var auto: Boolean = false,
        val admin: Boolean = false
)