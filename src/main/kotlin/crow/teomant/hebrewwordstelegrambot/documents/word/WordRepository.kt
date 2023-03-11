package crow.teomant.hebrewwordstelegrambot.documents.word

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface WordRepository : MongoRepository<Word, String> {
    fun findByRuIgnoreCase(ru: String): Word?
    fun findAllByIdNotIn(ids: List<ObjectId>): List<Word>
    fun findAllByIdNotInAndCategoriesContains(ids: List<ObjectId>, category: String): List<Word>
}