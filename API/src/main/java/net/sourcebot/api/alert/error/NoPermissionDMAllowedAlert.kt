package net.sourcebot.api.alert.error

import net.sourcebot.api.alert.ErrorAlert

/**
 * Called when a person does not have permission to use a command anywhere in the guild, but can in DMs
 */
class NoPermissionDMAllowedAlert : ErrorAlert(
    "No Permission!",
    "You don't have permission to use that command here, but you do in DMs!"
)