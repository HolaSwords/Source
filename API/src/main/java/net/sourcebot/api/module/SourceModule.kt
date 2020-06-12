package net.sourcebot.api.module

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.sourcebot.Source
import net.sourcebot.api.command.RootCommand
import net.sourcebot.api.properties.Properties
import org.slf4j.Logger
import java.io.File
import java.io.FileReader
import java.nio.file.Files

abstract class SourceModule {
    internal lateinit var moduleDescription: ModuleDescription
    lateinit var logger: Logger
        internal set

    var enabled: Boolean = false

    val dataFolder: File by lazy {
        File("modules", moduleDescription.name)
    }

    val config: Properties by lazy {
        val file = File(dataFolder, "config.json")
        if (!file.exists()) saveResource("/config.json")
        return@lazy FileReader(file).use {
            JsonParser.parseReader(it) as JsonObject
        }.let(::Properties)
    }

    fun saveResource(absPath: String): File {
        if (!absPath.startsWith("/")) throw IllegalArgumentException("Resource path must be absolute!")
        val relPath = absPath.substring(1)
        return this::class.java.getResourceAsStream(absPath).use {
            if (it == null) throw IllegalStateException("Could not locate '$relPath' in module JAR!")
            val asPath = dataFolder.toPath().resolve(relPath)
            dataFolder.mkdirs()
            Files.copy(it, asPath)
            asPath.toFile()
        }
    }

    /**
     * Fired when this Module is being loaded; before it is enabled.
     * Methods in this scope should not utilize API from other Modules.
     */
    open fun onLoad(source: Source) = Unit

    /**
     * Fired when this Module is being unloaded; after it is disabled.
     */
    open fun onUnload() = Unit

    /**
     * Fired when this Module is being enabled, after it is loaded.
     */
    open fun onEnable(source: Source) = Unit

    /**
     * Fired when this Module is being disabled, before it is unloaded.
     */
    open fun onDisable() = Unit

    fun registerCommands(source: Source, vararg command: RootCommand) {
        command.forEach {
            source.commandHandler.registerCommand(this, it)
        }
    }
}