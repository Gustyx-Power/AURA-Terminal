package terminal

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Command history and autocomplete suggestions manager */
class AutocompleteManager {
    private val commandHistory = mutableListOf<String>()
    var currentDirectory: String = System.getenv("HOME") ?: "/home"

    fun addToHistory(command: String) {
        val trimmed = command.trim()
        if (trimmed.isNotEmpty() && (commandHistory.isEmpty() || commandHistory.last() != trimmed)
        ) {
            commandHistory.add(trimmed)
            if (commandHistory.size > 1000) commandHistory.removeAt(0)
        }
    }

    suspend fun getGhostText(currentInput: String): String? {
        if (currentInput.isEmpty()) return null
        getHistorySuggestion(currentInput)?.let {
            return it.removePrefix(currentInput)
        }
        return getFilesystemSuggestion(currentInput)
    }

    private fun getHistorySuggestion(input: String) =
            commandHistory.asReversed().firstOrNull { it.startsWith(input) && it != input }

    private suspend fun getFilesystemSuggestion(input: String): String? =
            withContext(Dispatchers.IO) {
                try {
                    val words = input.split(" ")
                    val lastWord = words.lastOrNull() ?: return@withContext null

                    if (words.size >= 2) {
                        val command = words[0].lowercase()
                        if (command in
                                        listOf(
                                                "cd",
                                                "ls",
                                                "cat",
                                                "vim",
                                                "nano",
                                                "less",
                                                "more",
                                                "head",
                                                "tail",
                                                "rm",
                                                "cp",
                                                "mv",
                                                "mkdir"
                                        )
                        ) {
                            return@withContext getPathCompletion(lastWord)
                        }
                    }

                    if (lastWord.startsWith("/") ||
                                    lastWord.startsWith("~") ||
                                    lastWord.startsWith(".")
                    ) {
                        return@withContext getPathCompletion(lastWord)
                    }
                    null
                } catch (e: Exception) {
                    null
                }
            }

    private fun getPathCompletion(partialPath: String): String? {
        val expandedPath =
                when {
                    partialPath.startsWith("~") ->
                            partialPath.replaceFirst("~", System.getenv("HOME") ?: "/home")
                    !partialPath.startsWith("/") -> "$currentDirectory/$partialPath"
                    else -> partialPath
                }

        val file = File(expandedPath)
        val parent = file.parentFile ?: File(currentDirectory)
        val prefix = file.name

        if (!parent.exists() || !parent.isDirectory) {
            val currentDir = File(currentDirectory)
            if (currentDir.exists() && currentDir.isDirectory) {
                val matches =
                        currentDir
                                .listFiles()
                                ?.filter {
                                    it.name.startsWith(partialPath, ignoreCase = true) &&
                                            it.name != partialPath
                                }
                                ?.sortedBy { it.name }
                if (!matches.isNullOrEmpty()) {
                    val match = matches[0]
                    return match.name.removePrefix(partialPath) + if (match.isDirectory) "/" else ""
                }
            }
            return null
        }

        val matches =
                parent.listFiles()
                        ?.filter {
                            it.name.startsWith(prefix, ignoreCase = true) && it.name != prefix
                        }
                        ?.sortedBy { it.name }

        if (matches.isNullOrEmpty()) return null
        val match = matches[0]
        return match.name.removePrefix(prefix) + if (match.isDirectory) "/" else ""
    }

    suspend fun getFullCompletion(currentInput: String) =
            getGhostText(currentInput)?.let { currentInput + it }
    fun getHistory() = commandHistory.toList()
    fun getHistoryAt(index: Int) =
            if (index in 0 until commandHistory.size)
                    commandHistory[commandHistory.size - 1 - index]
            else null
    fun historySize() = commandHistory.size
}
