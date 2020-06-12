package net.sourcebot

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.requests.GatewayIntent.DIRECT_MESSAGE_TYPING
import net.dv8tion.jda.api.requests.GatewayIntent.GUILD_MESSAGE_TYPING
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import net.sourcebot.api.command.CommandHandler
import net.sourcebot.api.database.MongoDB
import net.sourcebot.api.database.MongoSerial
import net.sourcebot.api.event.EventSystem
import net.sourcebot.api.event.SourceEvent
import net.sourcebot.api.module.InvalidModuleException
import net.sourcebot.api.module.ModuleDescription
import net.sourcebot.api.module.ModuleHandler
import net.sourcebot.api.module.SourceModule
import net.sourcebot.api.permission.*
import net.sourcebot.api.properties.JsonSerial
import net.sourcebot.api.properties.Properties
import net.sourcebot.impl.command.GuildInfoCommand
import net.sourcebot.impl.command.HelpCommand
import net.sourcebot.impl.command.PermissionsCommand
import net.sourcebot.impl.command.TimingsCommand
import org.slf4j.LoggerFactory
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J
import java.io.File
import java.io.FileFilter
import java.io.FileReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList

/**
 * Entrypoint class for the Source process
 * This class is also an implementation of [SourceModule], providing base functionality
 *
 * @author Hunter Wignall
 * @version June 12, 2020
 */
class Source internal constructor(val properties: Properties) : SourceModule() {
    /**
     * Some [GatewayIntent]s that Source will ignore.
     * Ignores member and DM typing events.
     */
    private val ignoredIntents: EnumSet<GatewayIntent> = EnumSet.of(
        GUILD_MESSAGE_TYPING, DIRECT_MESSAGE_TYPING
    )

    /**
     * An [EventSystem] that handles [SourceEvent]s
     */
    val sourceEventSystem = EventSystem<SourceEvent>()

    /**
     * An [EventSystem] that handles [GenericEvent]s from JDA
     */
    val jdaEventSystem = EventSystem<GenericEvent>()

    /**
     * The [MongoDB] database utility
     */
    val mongodb = MongoDB(properties.required("mongodb"))

    /**
     * The [PermissionHandler] that is responsible for accessing [PermissionData]
     */
    val permissionHandler = PermissionHandler(mongodb)

    /**
     * The [ModuleHandler] that is responsible for [SourceModule] lifecycles
     */
    val moduleHandler = ModuleHandler(this)

    /**
     * The [CommandHandler] that is responsible for performing [net.sourcebot.api.command.Command]s
     */
    val commandHandler = CommandHandler(
        properties.required("commands.prefix"),
        properties.required("commands.delete-seconds"),
        permissionHandler
    )

    /**
     * The [ShardManager] that is responsible for managing JDA shards
     */
    val shardManager: ShardManager = DefaultShardManagerBuilder.create(
        properties.required("token"),
        EnumSet.complementOf(ignoredIntents)
    ).addEventListeners(
        EventListener(jdaEventSystem::fireEvent),
        object : ListenerAdapter() {
            override fun onMessageReceived(event: MessageReceivedEvent) {
                commandHandler.onMessageReceived(event)
            }
        }
    ).setActivityProvider {
        Activity.watching("TSC. Shard $it")
    }.build()

    init {
        // Load the module description from the root JAR
        moduleDescription = this.javaClass.getResourceAsStream("/module.json").use {
            if (it == null) throw InvalidModuleException("Could not find module.json!")
            else JsonParser.parseReader(InputStreamReader(it)) as JsonObject
        }.let(::ModuleDescription)

        // Populate the logger field
        logger = LoggerFactory.getLogger(Source::class.java)

        // Register Source MongoDB Serializers
        registerSerial()

        // Load modules from the modules folder
        loadModules()

        logger.info("Source is now online!")
    }

    /**
     * Registers MongoDB serializers to the [MongoSerial] interface
     */
    private fun registerSerial() {
        // Serializers for Source permission entities
        MongoSerial.register(SourcePermission.Serial())
        MongoSerial.register(SourceGroup.Serial(permissionHandler))
        MongoSerial.register(SourceUser.Serial(permissionHandler))
        MongoSerial.register(SourceRole.Serial(permissionHandler))
    }

    /**
     * Loads modules from the 'modules' folder into the [ModuleHandler]
     */
    private fun loadModules() {
        logger.debug("Indexing Modules...")
        moduleHandler.moduleIndex["Source"] = this
        moduleHandler.enableModule(this)
        val modulesFolder = File("modules")
        if (!modulesFolder.exists()) modulesFolder.mkdir()
        val indexed = modulesFolder.listFiles(FileFilter {
            it.name.endsWith(".jar")
        })!!.sortedWith(Comparator.comparing(File::getName)).mapNotNull { moduleHandler.indexModule(it) }
        logger.debug("Loading Modules...")
        val errored = ArrayList<String>()
        val loaded = indexed.mapNotNull {
            try {
                moduleHandler.loadModule(it)
            } catch (ex: Throwable) {
                when (ex) {
                    is StackOverflowError -> logger.error("Cyclic dependency problem for module '$it' !")
                    else -> logger.error("Error loading module '$it' !", ex)
                }
                errored.add(it)
                null
            }
        }
        errored.forEach(moduleHandler::unloadModule)
        logger.debug("All modules have been loaded, now enabling...")
        loaded.forEach(moduleHandler::enableModule)
    }

    /**
     * Entrypoint method for the Source base module
     * Registers basic commands and events
     */
    override fun onEnable(source: Source) {
        registerCommands(source,
            HelpCommand(moduleHandler, commandHandler),
            GuildInfoCommand(),
            TimingsCommand(),
            PermissionsCommand(permissionHandler)
        )
    }

    companion object {
        // A date and time format of MM/dd/yyyy hh:mm:ss a z
        @JvmField val DATE_TIME_FORMAT: DateTimeFormatter = ofPattern("MM/dd/yyyy hh:mm:ss a z")

        // A time format of hh:mm:ss a z
        @JvmField val TIME_FORMAT: DateTimeFormatter = ofPattern("hh:mm:ss a z")

        // A date format of MM/dd/yyyy
        @JvmField val DATE_FORMAT: DateTimeFormatter = ofPattern("MM/dd/yyyy")

        // The Source timezone
        @JvmField val TIME_ZONE: ZoneId = ZoneId.of("America/New_York")

        // Whether or not Source is currently enabled
        private var enabled = false

        @JvmStatic fun main(args: Array<String>) {
            // Redirect SysOut and SysErr to SLF4J when run as independent program
            SysOutOverSLF4J.sendSystemOutAndErrToSLF4J()

            //Start Source
            start()
        }

        @JvmStatic fun start(): Source {
            // Only one instance of Source should be live at a time
            if (enabled) throw IllegalStateException("Source is already enabled!")
            enabled = true

            // Register the Properties serializer before deserializing Properties
            JsonSerial.register(Properties.Serial())

            // Load the config.json, saving from the JAR if absent.
            val configFile = File("config.json")
            if (!configFile.exists()) {
                Source::class.java.getResourceAsStream("/config.example.json").use {
                    Files.copy(it, Path.of("config.json"))
                }
            }

            // Map the configuration to Properties and start Source
            return FileReader(configFile).use {
                JsonParser.parseReader(it) as JsonObject
            }.let(::Properties).let(::Source)
        }
    }
}