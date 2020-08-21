package net.sourcebot.api.command

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.TextChannel
import net.sourcebot.api.alert.Alert
import net.sourcebot.api.alert.ErrorAlert
import net.sourcebot.api.alert.error.ExceptionAlert
import net.sourcebot.api.alert.error.GlobalAdminOnlyAlert
import net.sourcebot.api.command.argument.Arguments
import net.sourcebot.api.event.AbstractMessageHandler
import net.sourcebot.api.module.SourceModule
import net.sourcebot.api.permission.PermissionHandler
import java.util.concurrent.TimeUnit

class CommandHandler(
        val prefix: String,
        private val deleteSeconds: Long,
        private val globalAdmins: Set<String>,
        private val permissionHandler: PermissionHandler
) : AbstractMessageHandler(prefix) {
    private var commandMap = CommandMap<RootCommand>()

    override fun cascade(
            message: Message, label: String, args: Array<String>
    ) {
        val author = message.author
        if (author.isFake || author.isBot) return

        val rootCommand = commandMap[label] ?: return
        if (!rootCommand.module.enabled) return

        val inGuild = message.channelType == ChannelType.TEXT
        if (rootCommand.guildOnly && !inGuild) {
            return respond(
                    rootCommand,
                    message,
                    GuildOnlyCommandAlert(),
                    rootCommand.cleanupResponse
            )
        }

        val arguments = Arguments(args)
        var command: Command = rootCommand
        val hasGlobalAdmin = author.id in globalAdmins

        do {
            if (!hasGlobalAdmin) {
                if (command.requiresGlobal) {
                    return respond(
                            command,
                            message,
                            GlobalAdminOnlyAlert(),
                            command.cleanupResponse
                    )
                }
                if (command.permission != null) {
                    hasPermission(command, message, inGuild)?.run {
                        return respond(
                                command,
                                message,
                                this,
                                command.cleanupResponse
                        )
                    }

                }
            }
            val nextId = arguments.next() ?: break
            val nextCommand = command[nextId]
            if (nextCommand == null) {
                arguments.backtrack()
                break
            }
            command = nextCommand
        } while (true)


        kotlin.runCatching {
            command.execute(message, arguments)
        }.recoverCatching {
            it.printStackTrace()
            if (it !is InvalidSyntaxException) ExceptionAlert(it)
            ErrorAlert(
                    "Invalid Syntax!",
                    """${it.message!!}
                    **Syntax:** ${getSyntax(command)}""".trimIndent()
            )
        }.onSuccess {
            respond(command, message, it, command.cleanupResponse)
        }
    }

    private fun hasPermission(command: Command, message: Message, inGuild: Boolean): Alert? {
        val permission = command.permission!!
        if (!inGuild && command.guildOnly) {
            return GuildOnlyCommandAlert()
        }

        if (!inGuild) return null

        val permissionData = permissionHandler.getData(message.guild)
        val guild = message.guild
        val member = message.member!!
        val roles = member.roles.toMutableList().apply {
            add(guild.publicRole)
        }

        if (roles.none { it.hasPermission(Permission.ADMINISTRATOR) }) {
            val sourceUser = permissionData.getUser(member)
            val sourceRoles = roles.map(permissionData::getRole).toSet()
            sourceUser.roles = sourceRoles
            val channel = message.channel as TextChannel
            if (!permissionHandler.hasPermission(sourceUser, permission, channel)) {
                return permissionHandler.getPermissionAlert(
                        command.guildOnly,
                        message.jda,
                        sourceUser,
                        permission
                )
            }
        }

        return null
    }

    private fun respond(command: Command, message: Message, alert: Alert, cleanup: Boolean) {
        message.channel.sendMessage(alert.asMessage(message.author)).queue {
            command.postResponse(it)
            if (!cleanup) return@queue
            if (message.channelType == ChannelType.TEXT) {
                message.delete().queueAfter(deleteSeconds, TimeUnit.SECONDS)
            }
            it.delete().queueAfter(deleteSeconds, TimeUnit.SECONDS)
        }
    }

    fun getSyntax(command: Command) = "$prefix${command.usage}".trim()

    fun getCommands(
            module: SourceModule
    ) = commandMap.getCommands().filter { it.module == module }

    fun getCommand(name: String) = commandMap[name.toLowerCase()]

    fun registerCommands(
            module: SourceModule,
            vararg command: RootCommand
    ) = command.forEach {
        it.module = module
        commandMap.register(it)
    }

    fun unregister(module: SourceModule) = commandMap.removeIf { it.module == module }

    /**
     * Called when a user uses a command marked as guildOnly outside of a Guild (i.e Direct Message)
     */
    private class GuildOnlyCommandAlert : ErrorAlert(
            "Guild Only Command!", "This command may not be used outside of a guild!"
    )
}