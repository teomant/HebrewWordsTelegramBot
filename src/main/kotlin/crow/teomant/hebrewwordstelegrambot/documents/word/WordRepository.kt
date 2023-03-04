package crow.teomant.hebrewwordstelegrambot.documents.word

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface WordRepository : MongoRepository<Word, String> {
    fun findByRuIgnoreCase(ru: String): Word?
}