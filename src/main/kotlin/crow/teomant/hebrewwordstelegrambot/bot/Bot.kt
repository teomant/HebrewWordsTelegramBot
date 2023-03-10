package crow.teomant.hebrewwordstelegrambot.bot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.polls.PollType
import crow.teomant.hebrewwordstelegrambot.documents.category.Category
import crow.teomant.hebrewwordstelegrambot.documents.category.CategoryRepository
import crow.teomant.hebrewwordstelegrambot.documents.user.User
import crow.teomant.hebrewwordstelegrambot.documents.user.UserCategory
import crow.teomant.hebrewwordstelegrambot.documents.user.UserRepository
import crow.teomant.hebrewwordstelegrambot.documents.user.UserWord
import crow.teomant.hebrewwordstelegrambot.documents.word.Word
import crow.teomant.hebrewwordstelegrambot.documents.word.WordRepository
import jakarta.annotation.PostConstruct
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

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
                if (checkIfNotPrivateChat()) return@command
                val user = getUser(userRepository)
                bot.sendMessage(ChatId.fromId(message.chat.id), "?????????????? ?????? ?? ???????????????????? ???? ???????????? ???? ????????????\n " +
                        "/word ?????? ?????????????????? ??????????\n " +
                        "/auto ?????? ???????????????????????? ?????????? ?????????????????? ???????????? ?????????? ?????????? ?????????????? ????????????\n" +
                        "/addWords ?????? ???????????????????? ???????? ?????????? ???????? ?? ???????? ?????????????????? ??????????\n" +
                        "/addWordsFromCategory ?????? ???????????????????? ???????? ?????????? ???????? ?? ???????? ?????????????????? ?????????? ???? ???????????????????? ??????????????????\n" +
                        "/addPreferableCategory ?????????????? ???????????????????????????? ?????????????????? ???? ???????????? ??????????????\n" +
                        "/removePreferableCategory ???????????? ?????????????????? ???? ???????????? ????????????????????????????\n" +
                        "/clearPreferableCategory ???????????????? ???????????? ????????????????????????????\n" +
                        "/clearWords ???????????????? ??????????????\n")
                if (user.admin) {
                    bot.sendMessage(ChatId.fromId(message.chat.id), "???? ??????????, ????????????????????!")
                }

            }
            command("word") {
                if (checkIfNotPrivateChat()) return@command

                val user = getUser(userRepository)

                sendQuiz(this.bot, this.message.chat.id, user, wordRepository, userRepository)
            }
            command("addWords") {
                if (checkIfNotPrivateChat()) return@command

                val user = getUser(userRepository)
                val newWords = wordRepository.findAllByIdNotIn(user.words.map { it.id }.map { ObjectId(it) })
                        .asSequence()
                        .shuffled()
                        .take(10)

                user.words.addAll(newWords.map { UserWord(it.id.toString(), Date(), Date(), it.categories) })
                try {
                    userRepository.save(user)
                } catch (e: Exception) {
                    println(e)
                }

                bot.sendMessage(ChatId.fromId(user.telegramId.toLong()), "?????????????????? ??????????: ${newWords.joinToString { "${it.he} (${it.ru})" }}")
            }
            command("addWordsFromCategory") {
                if (checkIfNotPrivateChat()) return@command

                val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
                        categoryRepository.findAll().map {
                            listOf(InlineKeyboardButton.CallbackData(text = it.name, callbackData = "awfc.${it.id.toString()}"))
                        }
                )
                bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "?????????????????? ??????????????????:",
                        replyMarkup = inlineKeyboardMarkup
                )
            }
            command("addPreferableCategory") {
                if (checkIfNotPrivateChat()) return@command

                val user = getUser(userRepository)
                val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
                        categoryRepository.findAllById(user.words.map { it.categories }.flatMap { it })
                                .map {
                                    listOf(InlineKeyboardButton.CallbackData(text = it.name, callbackData = "apc.${it.id}"))
                                }
                )
                bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "?????????????????? ??????????????????:",
                        replyMarkup = inlineKeyboardMarkup
                )
            }
            command("removePreferableCategory") {
                if (checkIfNotPrivateChat()) return@command

                val user = getUser(userRepository)
                val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
                        user.preferable.map {
                            listOf(InlineKeyboardButton.CallbackData(text = it.name, callbackData = "rpc.${it.id}"))
                        }
                )
                bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "?????????????????? ??????????????????:",
                        replyMarkup = inlineKeyboardMarkup
                )
            }
            command("clearPreferableCategory") {
                if (checkIfNotPrivateChat()) return@command

                val user = getUser(userRepository)
                user.preferable.clear()
                userRepository.save(user)
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "??????????????")
            }
            command("auto") {
                if (checkIfNotPrivateChat()) return@command

                val user = getUser(userRepository)
                user.auto = !user.auto
                userRepository.save(user)

                bot.sendMessage(ChatId.fromId(message.chat.id), "?????????????????????? ??????????????")
            }
            command("clearWords") {
                if (checkIfNotPrivateChat()) return@command

                val user = getUser(userRepository)
                user.words.clear()
                userRepository.save(user)

                bot.sendMessage(ChatId.fromId(message.chat.id), "?????????????? ??????????????")
            }
            command("importCategory") {
                if (checkIfNotPrivateChat()) return@command

                val user = getUser(userRepository)
                if (!user.admin) return@command

                val category: CategoryImport = objectMapper.readValue(
                        message.text?.substringAfter("/importCategory ")!!
                )

                categoryRepository.save(Category(ObjectId.get(), category.name, category.label, mutableSetOf(), user))
            }
            command("importWords") {
                if (checkIfNotPrivateChat()) return@command

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
            callbackQuery {
                if (callbackQuery.data.startsWith("awfc.")) {
                    val user = getUser(userRepository)
                    val newWords = wordRepository.findAllByIdNotInAndCategoriesContains(
                            user.words.map { it.id }.map { ObjectId(it) },
                            callbackQuery.data.substringAfter(".")
                    )
                            .asSequence()
                            .shuffled()
                            .take(10)

                    user.words.addAll(newWords.map { UserWord(it.id.toString(), Date(), Date(), it.categories) })
                    try {
                        userRepository.save(user)
                    } catch (e: Exception) {
                        println(e)
                    }

                    bot.sendMessage(ChatId.fromId(user.telegramId.toLong()), "?????????????????? ??????????: ${newWords.joinToString { "${it.he} (${it.ru})" }}")
                }
                if (callbackQuery.data.startsWith("apc.")) {
                    val user = getUser(userRepository)
                    val category = categoryRepository.findById(callbackQuery.data.substringAfter("."))
                            .orElseThrow()
                    user.preferable.add(UserCategory(category.id.toString(), category.name))

                    try {
                        userRepository.save(user)
                    } catch (e: Exception) {
                        println(e)
                    }

                    bot.sendMessage(ChatId.fromId(user.telegramId.toLong()), "?????????????????? ?????????????????? ?? ????????????????????????????")
                }
                if (callbackQuery.data.startsWith("rpc.")) {
                    val user = getUser(userRepository)
                    user.preferable.removeIf { it.id.equals(callbackQuery.data.substringAfter(".")) }

                    try {
                        userRepository.save(user)
                    } catch (e: Exception) {
                        println(e)
                    }

                    bot.sendMessage(ChatId.fromId(user.telegramId.toLong()), "?????????????????? ?????????????? ???? ????????????????????????????")
                }
            }
            pollAnswer {
                val user = userRepository.findByTelegramId(pollAnswer.user.id.toString())!!
                if (user.auto) {
                    sendQuiz(this.bot, this.pollAnswer.user.id, user, wordRepository, userRepository)
                }
            }
        }
    }.startPolling()
}

private fun sendQuiz(bot: Bot, id: Long, user: User, wordRepository: WordRepository, userRepository: UserRepository) {
    val userWords = if (user.preferable.isEmpty())
        user.words else user.words.filter { it.categories.any { cat -> user.preferable.any { pref -> pref.id == cat } } }

    if (userWords.size < 4) {
        bot.sendMessage(ChatId.fromId(id), "?????????????????? ?????????? ?????????????? ??????")
    }
    val selected = userWords.toMutableList()
            .shuffled()
            .asSequence()
            .take(6)
            .toMutableSet()

    selected.addAll(userWords.sortedBy { it.lastUsed }.take(5).shuffled().take(3))
    selected.addAll(userWords.sortedBy { it.lastUsed }.dropLast(3).sortedBy { it.added }.takeLast(10).shuffled().take(3))

    val words = wordRepository.findAllById(
            selected.sortedBy { it.lastUsed }.dropLast(3).shuffled().take(5).toList().map { it.id }
    ).toList()

    val correct = words.shuffled()[0]
    val shuffled = words.shuffled().map { it.ru }.toList()

    userWords.stream().filter { it.id == correct.id.toString() }.findAny().get().lastUsed = Date()

    userRepository.save(user)

    bot.sendPoll(
            chatId = ChatId.fromId(id),
            type = PollType.QUIZ,
            question = "?????? ?????????????????????? ${correct.he} (${correct.tr})?",
            options = shuffled,
            correctOptionId = shuffled.indexOf(correct.ru),
            isAnonymous = false
    )
}


private fun CommandHandlerEnvironment.checkIfNotPrivateChat(): Boolean {
    if (message.chat.type != "private") {
        bot.sendMessage(ChatId.fromId(message.chat.id), "???????? ?????? ???????? ???? ????????????, ?????????????? ???????? ????????????")
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
        bot.sendMessage(ChatId.fromId(message.chat.id), "????????????, ???? ???????? ???????????????????? ????????????????????????")
        throw e
    }
}

private fun CallbackQueryHandlerEnvironment.getUser(userRepository: UserRepository): User {
    val adminExist = userRepository.findByAdminTrue()?.admin ?: false
    try {
        return userRepository.findByTelegramId(callbackQuery.from.id.toString())
                ?: userRepository.save(User(ObjectId.get(), callbackQuery.from.id.toString(), mutableSetOf(), admin = !adminExist))
    } catch (e: Exception) {
        bot.sendMessage(ChatId.fromId(callbackQuery.from.id), "????????????, ???? ???????? ???????????????????? ????????????????????????")
        throw e
    }
}