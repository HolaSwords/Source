package net.sourcebot.api.alert.error

import net.sourcebot.api.alert.ErrorAlert

/**
 * Called when a person does not have permission to use a command.
 */
class NoPermissionAlert : ErrorAlert(
    "No Permission!",
    "You do not have permission to use that command!"
)