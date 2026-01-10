package terminal

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages command history and provides autocomplete suggestions. Supports both history-based and
 * filesystem-based completions.
 */
class AutocompleteManager {

    // Command history storage
    private val commandHistory = mutableListOf<String>()

    // Current working directory for filesystem suggestions
    var currentDirectory: String = System.getenv("HOME") ?: "/home"

    /** Adds a command to history. */
    fun addToHistory(command: String) {
        val trimmed = command.trim()
        if (trimmed.isNotEmpty() && (commandHistory.isEmpty() || commandHistory.last() != trimmed)
        ) {
            commandHistory.add(trimmed)
            // Keep history limited to 1000 entries
            if (commandHistory.size > 1000) {
                commandHistory.removeAt(0)
            }
        }
    }

    /**
     * Gets ghost text suggestion for the current input. Returns the completion part only (not the
     * full suggestion).
     */
    suspend fun getGhostText(currentInput: String): String? {
        if (currentInput.isEmpty()) return null

        // First, try history-based completion
        val historyMatch = getHistorySuggestion(currentInput)
        if (historyMatch != null) {
            return historyMatch.removePrefix(currentInput)
        }

        // Then, try filesystem-based completion for paths
        val filesystemMatch = getFilesystemSuggestion(currentInput)
        if (filesystemMatch != null) {
            return filesystemMatch
        }

        return null
    }

    /** Gets suggestion from command history. */
    private fun getHistorySuggestion(input: String): String? {
        // Search from most recent to oldest
        return commandHistory.asReversed().firstOrNull { it.startsWith(input) && it != input }
    }

    /** Gets filesystem-based suggestion for path completion. */
    private suspend fun getFilesystemSuggestion(input: String): String? =
            withContext(Dispatchers.IO) {
                try {
                    // Check if input contains a path-like pattern
                    val words = input.split(" ")
                    val lastWord = words.lastOrNull() ?: return@withContext null

                    // Handle commands like "cd Do" -> suggest "cuments" for "Documents"
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
                            val partialPath = lastWord
                            return@withContext getPathCompletion(partialPath)
                        }
                    }

                    // Also try direct path completion
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

    /** Gets path completion for a partial path. */
    private fun getPathCompletion(partialPath: String): String? {
        val expandedPath =
                if (partialPath.startsWith("~")) {
                    partialPath.replaceFirst("~", System.getenv("HOME") ?: "/home")
                } else if (!partialPath.startsWith("/")) {
                    "$currentDirectory/$partialPath"
                } else {
                    partialPath
                }

        val file = File(expandedPath)
        val parent = file.parentFile ?: File(currentDirectory)
        val prefix = file.name

        if (!parent.exists() || !parent.isDirectory) {
            // Try current directory directly
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
                    val completion = match.name.removePrefix(partialPath)
                    return if (match.isDirectory) "$completion/" else completion
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
        val completion = match.name.removePrefix(prefix)
        return if (match.isDirectory) "$completion/" else completion
    }

    /** Gets the full completion (original input + ghost text). */
    suspend fun getFullCompletion(currentInput: String): String? {
        val ghostText = getGhostText(currentInput) ?: return null
        return currentInput + ghostText
    }

    /** Gets command history for navigation (up/down arrows). */
    fun getHistory(): List<String> = commandHistory.toList()

    /** Gets history entry at index (for up/down navigation). */
    fun getHistoryAt(index: Int): String? {
        if (index < 0 || index >= commandHistory.size) return null
        return commandHistory[commandHistory.size - 1 - index]
    }

    fun historySize(): Int = commandHistory.size
}
