package net.sourcebot.api.command

import net.dv8tion.jda.api.entities.Message
import net.sourcebot.api.alert.Alert
import net.sourcebot.api.command.argument.Argument
import net.sourcebot.api.command.argument.ArgumentInfo
import net.sourcebot.api.command.argument.Arguments

/**
 * Represents a command to be executed via tha [CommandHandler]
 */
abstract class Command {
    // The children Commands for this Command
    private val children = CommandMap<Command>()

    // The name or primary label of this command
    abstract val name: String

    // The description of this command to be shown in help information
    abstract val description: String

    // The aliases of this command, default to an empty array
    open val aliases = emptyArray<String>()

    // The argument info for this command, default to show available subcommands
    open val argumentInfo: ArgumentInfo by lazy {
        val children = children.getCommandNames()
        if (children.isEmpty()) ArgumentInfo()
        else ArgumentInfo(
            Argument(children.joinToString("|"), "The subcommand you wish to perform")
        )
    }

    // Whether or not this command will have its response message deleted
    open var cleanupResponse = true

    // If this command has a parent, used for computing syntax
    open var parent: Command? = null

    // The permission for this command, if any
    open val permission: String? = null

    // Whether or not this command can only be used in a guild
    open val guildOnly = false

    // The usage of this command to be shown in help information
    val usage: String by lazy {
        var parent = this.parent
        var parentStr = this.name
        while (parent != null) {
            parentStr = "${parent.name} $parentStr"
            parent = parent.parent
        }
        "$parentStr ${argumentInfo.asList()}"
    }

    /**
     * Gets a child from the [children] [CommandMap]
     *
     * @param[identifier] An identifier for the desired child; primary label or an alias
     * @return The found [Command] or null
     */
    fun getChild(identifier: String) = children[identifier]

    /**
     * Executes this command response and returns an [Alert]
     *
     * @param[message] The [Message] that triggered this event
     * @param[args] The [Arguments] supplied to this [Command]
     *
     * @return The [Alert] response
     */
    open fun execute(message: Message, args: Arguments): Alert =
        throw InvalidSyntaxException("Invalid Subcommand!")

    /**
     * Adds a child [Command]
     *
     * @param[command] The [Command] to add as a child
     */
    protected fun addChild(command: Command) {
        children.register(command)
        command.parent = this
    }


    /**
     * Adds multiple [Command]s as children
     * @param[command] The [Command]s to add as children
     */
    protected fun addChildren(vararg command: Command) = command.forEach(::addChild)
}