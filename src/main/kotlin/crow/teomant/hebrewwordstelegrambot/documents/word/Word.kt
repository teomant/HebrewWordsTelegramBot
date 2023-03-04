package crow.teomant.hebrewwordstelegrambot.documents.word

import crow.teomant.hebrewwordstelegrambot.documents.user.User
import lombok.EqualsAndHashCode
import lombok.ToString
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "hebrew.word")
@ToString(of = ["id", "ru", "he"])
@EqualsAndHashCode(of = ["id"])
data class Word(
        @Id
        val id: ObjectId,
        @Indexed(unique = true)
        val ru: String,
        val he: String,
        val tr: String,
        val categories: MutableSet<String>,
        @DBRef
        val author: User
)