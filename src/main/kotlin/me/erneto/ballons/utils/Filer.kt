package me.erneto.ballons.utils

import java.io.File

object Filer {

    /**
     * Ensures that the file name ends with .yml extension
     */
    fun fixName(name: String): String {
        return if (name.endsWith(".yml")) name else "$name.yml"
    }

    /**
     * Creates the necessary folders for the plugin
     */
    fun createFolders() {
        val dataFolder = File("plugins/PaleBalloons/data")
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
    }

    /**
     * Checks if a file exists
     */
    fun fileExists(path: String): Boolean {
        return File(path).exists()
    }

    /**
     * Creates a file if it doesn't exist
     */
    fun createFile(path: String): Boolean {
        val file = File(path)
        return if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        } else {
            false
        }
    }

    /**
     * Deletes a file if it exists
     */
    fun deleteFile(path: String): Boolean {
        val file = File(path)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Lists all files in a directory with a specific extension
     */
    fun listFiles(directory: String, extension: String = "yml"): List<File> {
        val dir = File(directory)
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }

        return dir.listFiles { _, name ->
            name.endsWith(".$extension", ignoreCase = true)
        }?.toList() ?: emptyList()
    }

    /**
     * Copies a file from one location to another
     */
    fun copyFile(source: String, destination: String): Boolean {
        return try {
            val sourceFile = File(source)
            val destFile = File(destination)

            if (!sourceFile.exists()) {
                return false
            }

            destFile.parentFile?.mkdirs()
            sourceFile.copyTo(destFile, overwrite = true)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}