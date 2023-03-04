package crow.teomant.hebrewwordstelegrambot.bot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.polls.PollType
import crow.teomant.hebrewwordstelegrambot.documents.category.Category
import crow.teomant.hebrewwordstelegrambot.documents.category.CategoryRepository
import crow.teomant.hebrewwordstelegrambot.documents.user.User
import crow.teomant.hebrewwordstelegrambot.documents.user.UserRepository
import crow.teomant.hebrewwordstelegrambot.documents.word.Word
import crow.teomant.hebrewwordstelegrambot.documents.word.WordRepository
import jakarta.annotation.PostConstruct
import org.apache.logging.log4j.util.Strings
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class BotStarter(
        val userRepository: UserRepository,
        val wordRepository: WordRepository,
        val categoryRepository: CategoryRepository,
        val objectMapper: ObjectMapper
) {

    @Value("\${telegram.bot.token}")
    lateinit var botToken: String

    @PostConstruct
    fun init() {
        startBot(botToken, userRepository, wordRepository, categoryRepository, objectMapper)
    }
}

fun startBot(
        botToken: String,
        userRepository: UserRepository,
        wordRepository: WordRepository,
        categoryRepository: CategoryRepository,
        objectMapper: ObjectMapper
) {
    val bot = bot {
        token = botToken
        dispatch {
            command("start") {
                if (checkIfPrivateChat()) return@command
                val user = getUser(userRepository)
                bot.sendMessage(ChatId.fromId(message.chat.id), "Простой бот с викториной по словам на иврите, " +
                        "/word для получения слова, /auto для переключения опции получения нового слова после каждого ответа" +
                        "/addWords для добавления пяти новых слов в свой словарный запас")
                if (user.admin) {
                    bot.sendMessage(ChatId.fromId(message.chat.id), "Ты админ, поздравляю!")
                }

            }
            command("word") {
                if (checkIfPrivateChat()) return@command

                val user = getUser(userRepository)

                sendQuiz(this.bot, this.message.chat.id, user, wordRepository)
            }
            command("addWords") {
                if (checkIfPrivateChat()) return@command

                val user = getUser(userRepository)
                val newWords = wordRepository.findAll()
                        .filter { !user.words.contains(it.id.toString()) }
                        .shuffled()
                        .asSequence()
                        .take(5)

                user.words.addAll(newWords.map { it.id.toString() })
                userRepository.save(user)

                bot.sendMessage(ChatId.fromId(message.chat.id), "Добавлены слова: ${newWords.joinToString { "${it.he} (${it.ru})" }}")
            }
            command("auto") {
                if (checkIfPrivateChat()) return@command

                val user = getUser(userRepository)
                user.auto = !user.auto
                userRepository.save(user)

                bot.sendMessage(ChatId.fromId(message.chat.id), "Переключено успешно")
            }
            command("importCategory") {
                if (checkIfPrivateChat()) return@command

                val user = getUser(userRepository)
                if (!user.admin) return@command

                val category: CategoryImport = objectMapper.readValue(
                        message.text?.substringAfter("/importCategory ")!!
                )

                categoryRepository.save(Category(ObjectId.get(), category.name, category.label, mutableSetOf(), user))
            }
            command("importWords") {
                if (checkIfPrivateChat()) return@command

                val user = getUser(userRepository)
                if (!user.admin) return@command

                val wordsMap: Map<String, List<WordImport>> = objectMapper.readValue(
                        message.text?.substringAfter("/importWords ")!!
                )

                wordsMap.forEach { (categoryName, list) ->
                    run {
                        val category = categoryRepository.findByLabel(categoryName)!!
                        val savedWords = list.stream()
                                .map {
                                    wordRepository.findByRuIgnoreCase(it.ru)
                                            ?: wordRepository.save(Word(ObjectId.get(), it.ru, it.he, it.tr, mutableSetOf(category.id.toString()), user))
                                }
                                .toList()

                        category.words.addAll(savedWords.stream().map { it.id.toString() }.toList())
                        categoryRepository.save(category)
                    }
                }
            }
            pollAnswer {
                val user = userRepository.findByTelegramId(pollAnswer.user.id.toString())!!
                if (user.auto) {
                    sendQuiz(this.bot, this.pollAnswer.user.id, user, wordRepository)
                }
            }
            text {
                if (!(message.text?.startsWith("/") ?: true)) {
                    bot.sendMessage(ChatId.fromId(message.chat.id), "${message.text}")
                }
            }
        }
    }.startPolling()
}

private fun sendQuiz(bot: Bot, id: Long, user: User, wordRepository: WordRepository) {
    if (user.words.size < 4) {
        bot.sendMessage(ChatId.fromId(id), "Словарный запас слишком мал")
    }
    val words = wordRepository.findAllById(
            user.words.toMutableList()
                    .shuffled()
                    .asSequence()
                    .take(4)
                    .toList()
    )

    val correct = words.shuffled()[0]
    val shuffled = words.shuffled().map { it.ru }.toList()

    bot.sendPoll(
            chatId = ChatId.fromId(id),
            type = PollType.QUIZ,
            question = "Как переводится ${correct.he} (${correct.tr})?",
            options = shuffled,
            correctOptionId = shuffled.indexOf(correct.ru),
            isAnonymous = false
    )
}


private fun CommandHandlerEnvironment.checkIfPrivateChat(): Boolean {
    if (message.chat.type != "private") {
        bot.sendMessage(ChatId.fromId(message.chat.id), "меня тут быть не должно, уберите меня отсюда")
        return true
    }
    return false
}

private fun CommandHandlerEnvironment.getUser(userRepository: UserRepository): User {
    val adminExist = userRepository.findByAdminTrue()?.admin ?: false
    try {
        return userRepository.findByTelegramId(message.from?.id?.toString()!!)
                ?: userRepository.save(User(ObjectId.get(), message.chat.id.toString(), mutableSetOf(), admin = !adminExist))
    } catch (e: Exception) {
        bot.sendMessage(ChatId.fromId(message.chat.id), "Ошибка, не могу установить пользователя")
        throw e;
    }
}