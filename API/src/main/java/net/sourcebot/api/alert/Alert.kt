package net.sourcebot.api.alert

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import java.awt.Color
import java.time.Instant


/**
 * Represents a message that has been personalized for some [User]
 * Alerts are used as responses to Commands
 */
interface Alert {
    fun asMessage(user: User): Message
}

/**
 * An EmbedAlert represents an embed with some basic information pre-attached.
 * Resultant embeds will be personalized for a [User] by rendering their profile photo as the author thumbnail.
 * EmbedAlerts also have a timestamp and footer.
 */
abstract class EmbedAlert @JvmOverloads internal constructor(
    protected var title: String,
    protected var description: String? = null
) : EmbedBuilder(), Alert {
    override fun asMessage(user: User): Message {
        setAuthor(title, null, user.effectiveAvatarUrl)
        setDescription(description)
        setTimestamp(Instant.now())
        setFooter("TheSourceCode • https://sourcebot.net")
        return MessageBuilder(super.build()).build()
    }

    @Throws(UnsupportedOperationException::class)
    override fun build() =
        throw UnsupportedOperationException("Alerts may not be built raw! Use buildFor instead!")
}

/**
 * Represents an EmbedAlert with a given color.
 */
abstract class ColoredAlert @JvmOverloads internal constructor(
    title: String,
    description: String? = null,
    color: Color
) : EmbedAlert(title, description) {

    @JvmOverloads internal constructor(
        title: String,
        description: String? = null,
        sourceColor: SourceColor
    ) : this(title, description, sourceColor.color)

    init {
        setColor(color)
    }
}

/**
 * Represents a [ColoredAlert] using the color [SourceColor.INFO]
 */
open class InfoAlert @JvmOverloads constructor(
    title: String,
    description: String? = null
) : ColoredAlert(title, description, SourceColor.INFO)

/**
 * Represents a [ColoredAlert] using the color [SourceColor.SUCCESS]
 */
open class SuccessAlert @JvmOverloads constructor(
    title: String,
    description: String? = null
) : ColoredAlert(title, description, SourceColor.SUCCESS)

/**
 * Represents a [ColoredAlert] using the color [SourceColor.WARNING]
 */
open class WarningAlert @JvmOverloads constructor(
    title: String,
    description: String? = null
) : ColoredAlert(title, description, SourceColor.WARNING)

/**
 * Represents a [ColoredAlert] using the color [SourceColor.ERROR]
 */
open class ErrorAlert @JvmOverloads constructor(
    title: String,
    description: String? = null
) : ColoredAlert(title, description, SourceColor.ERROR)