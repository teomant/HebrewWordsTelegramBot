package crow.teomant.hebrewwordstelegrambot.documents.user

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : MongoRepository<User, String> {
    fun findByTelegramId(id: String): User?
    fun findByAdminTrue(): User?
}