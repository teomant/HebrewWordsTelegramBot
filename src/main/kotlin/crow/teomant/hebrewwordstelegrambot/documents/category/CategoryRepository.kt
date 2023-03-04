package crow.teomant.hebrewwordstelegrambot.documents.category

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CategoryRepository : MongoRepository<Category, String> {
    fun findByLabel(label: String): Category?
}