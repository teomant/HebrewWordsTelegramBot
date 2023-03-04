package crow.teomant.hebrewwordstelegrambot.documents.category

import crow.teomant.hebrewwordstelegrambot.documents.user.User
import lombok.EqualsAndHashCode
import lombok.ToString
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "hebrew.category")
@ToString(of = ["id", "name", "label"])
@EqualsAndHashCode(of = ["id"])
data class Category(
        @Id
        val id: ObjectId,
        @Indexed(unique = true)
        val name: String,
        @Indexed(unique = true)
        val label: String,
        val words: MutableSet<String>,
        @DBRef
        val author: User
)