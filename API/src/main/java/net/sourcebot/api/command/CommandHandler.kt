package net.sourcebot.api.command

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.TextChannel
import net.sourcebot.api.alert.Alert
import net.sourcebot.api.alert.ErrorAlert
import net.sourcebot.api.alert.error.ExceptionAlert
import net.sourcebot.api.command.argument.Arguments
import net.sourcebot.api.event.AbstractMessageHandler
import net.sourcebot.api.module.SourceModule
import net.sourcebot.api.permission.PermissionHandler
import java.util.concurrent.TimeUnit

/**
 * The [CommandHandler] is responsible for holding reference to all registered commands
 *
 * @author Hunter Wignall
 * @version June 12, 2020
 */
class CommandHandler(
    private val prefix: String,
    private val deleteSeconds: Long,
    private val permissionHandler: PermissionHandler
) : AbstractMessageHandler(prefix) {
    private var commandMap = CommandMap<RootCommand>()

    /**
     * Cascades through the [CommandMap] to find the proper command to execute
     */
    override fun cascade(
        message: Message, label: String, args: Array<String>
    ) {
        val author = message.author
        // Ignore messages from bots
        if (author.isBot) return
        // Find an available command
        val rootCommand = commandMap[label] ?: return
        // Make sure the command module is enabled
        if (!rootCommand.module.enabled) return
        // Was this message sent in a guild?
        val inGuild = message.channelType == ChannelType.TEXT
        // If the command is guildOnly and this is not a guild, send GuildOnlyCommandAlert
        if (rootCommand.guildOnly && !inGuild) {
            return respond(
                message,
                GuildOnlyCommandAlert(),
                true
            )
        }
        // Determine the arguments
        val arguments = Arguments(args)
        // Iterate command children, checking permissions at each step
        var command: Command = rootCommand
        do {
            // If the command has a permission
            if (command.permission != null) {
                val permission = command.permission!!
                // If the command was used in a guild
                if (inGuild) {
                    // Get the guild permission data
                    val permissionData = permissionHandler.getData(message.guild)
                    val guild = message.guild
                    val member = message.member!!
                    // Get all member roles, including @everyone
                    val roles = member.roles.toMutableList().apply {
                        add(guild.publicRole)
                    }
                    // Check for Permission.ADMINISTRATOR across all roles
                    if (roles.none { it.hasPermission(Permission.ADMINISTRATOR) }) {
                        // Get the SourceUser for the author
                        val sourceUser = permissionData.getUser(member)
                        val sourceRoles = roles.map(permissionData::getRole).toSet()
                        // Recompute SourceUser's SourceRoles
                        sourceUser.roles = sourceRoles
                        val channel = message.channel as TextChannel
                        // Check for all effective permission nodes
                        if (!permissionHandler.hasPermission(sourceUser, permission, channel)) {
                            return respond(
                                message,
                                permissionHandler.getPermissionAlert(
                                    command.guildOnly,
                                    message.jda,
                                    sourceUser,
                                    permission
                                ),
                                true
                            )
                        }
                    }
                    // GuildOnlyCommandAlert if the command is guildOnly
                } else if (command.guildOnly) {
                    return respond(
                        message,
                        GuildOnlyCommandAlert(),
                        true
                    )
                }
            }
            // Get the next identifier or break the loop
            val nextId = arguments.next() ?: break
            // Get the next command or backtrack args and break the loop
            val nextCommand = command.getChild(nextId)
            if (nextCommand == null) {
                arguments.backtrack()
                break
            }
            command = nextCommand
        } while (true)
        // Try to perform command response or send ExceptionAlert if there was a problem
        val response = try {
            command.execute(message, arguments)
        } catch (ex: Exception) {
            handleException(command, ex)
        }
        // Send the final response, cleaning up if necessary
        return respond(message, response, command.cleanupResponse)
    }

    /**
     * Responds to a command with a given [Alert] and cleans up
     * @param[message] The [Message] that triggered the command
     * @param[alert] The [Alert] to send
     * @param[cleanup] Whether or not the [Message] and [Alert] will be deleted
     */
    private fun respond(message: Message, alert: Alert, cleanup: Boolean) {
        message.channel.sendMessage(alert.asMessage(message.author)).queue {
            if (!cleanup) return@queue
            if (message.channelType == ChannelType.TEXT) {
                message.delete().queueAfter(deleteSeconds, TimeUnit.SECONDS)
            }
            it.delete().queueAfter(deleteSeconds, TimeUnit.SECONDS)
        }
    }

    /**
     * Catches [Exception]s and puts them through an [ExceptionAlert]
     * Catches [InvalidSyntaxException] and renders them in an [ErrorAlert]
     */
    private fun handleException(command: Command, exception: Exception) =
        if (exception is InvalidSyntaxException) {
            ErrorAlert(
                "Invalid Syntax!",
                "${exception.message!!}\n" +
                "**Syntax:** ${getSyntax(command)}"
            )
        } else ExceptionAlert(exception)

    /**
     * Gets the syntax of a given [Command]
     */
    fun getSyntax(command: Command) = "$prefix${command.usage}".trim()

    /**
     * Get all [RootCommand]s for a specific module
     * @param[module] The [SourceModule] to get commands for
     */
    fun getCommands(module: SourceModule) =
        commandMap.getCommands().filter { it.module == module }

    /**
     * Gets a [RootCommand] from the [CommandMap]
     * @return The found [RootCommand] or null
     */
    fun getCommand(name: String) = commandMap[name.toLowerCase()]

    /**
     * Registers a [RootCommand] to a given [SourceModule]
     */
    fun registerCommand(module: SourceModule, command: RootCommand) {
        command.module = module
        commandMap.register(command)
    }

    /**
     * Unregisters commands beloning to the supplied [SourceModule]
     * @param[module] The [SourceModule] to remove for
     */
    fun unregister(module: SourceModule) = commandMap.removeIf { it.module == module }

    /**
     * Called when a user uses a command marked as guildOnly outside of a Guild (i.e Direct Message)
     */
    private class GuildOnlyCommandAlert : ErrorAlert(
        "Guild Only Command!", "This command may not be used outside of a guild!"
    )
}