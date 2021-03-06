package net.sourcebot.impl

import net.sourcebot.Source
import net.sourcebot.api.module.InvalidModuleException
import net.sourcebot.api.module.ModuleClassLoader
import net.sourcebot.api.module.ModuleDescriptor
import net.sourcebot.api.module.SourceModule
import net.sourcebot.api.properties.JsonSerial
import net.sourcebot.impl.command.GuildInfoCommand
import net.sourcebot.impl.command.HelpCommand
import net.sourcebot.impl.command.PermissionsCommand
import net.sourcebot.impl.command.TimingsCommand
import net.sourcebot.impl.command.lifecycle.RestartCommand
import net.sourcebot.impl.command.lifecycle.StopCommand
import net.sourcebot.impl.command.lifecycle.UpdateCommand

class BaseModule(
    private val source: Source
) : SourceModule() {
    init {
        classLoader = object : ModuleClassLoader() {
            override fun findClass(name: String, searchParent: Boolean): Class<*> {
                return try {
                    if (searchParent) source.moduleHandler.findClass(name)
                    else source.javaClass.classLoader.loadClass(name)
                } catch (ex: Exception) {
                    null
                } ?: throw ClassNotFoundException(name)
            }
        }
        descriptor = this.javaClass.getResourceAsStream("/module.json").use {
            if (it == null) throw InvalidModuleException("Could not find module.json!")
            else JsonSerial.mapper.readTree(it)
        }.let(::ModuleDescriptor)
    }

    override fun onEnable(source: Source) {
        source.commandHandler.registerCommands(
            this,
            HelpCommand(source.moduleHandler, source.commandHandler),
            GuildInfoCommand(),
            TimingsCommand(),
            PermissionsCommand(source.permissionHandler),

            RestartCommand(source.properties.required("lifecycle.restart")),
            StopCommand(source.properties.required("lifecycle.stop")),
            UpdateCommand(source.properties.required("lifecycle.update"))
        )
    }
}