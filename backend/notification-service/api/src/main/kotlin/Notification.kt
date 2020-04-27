package dk.sdu.cloud.notification.api

import dk.sdu.cloud.FindByLongId

typealias NotificationId = Long
typealias FindByNotificationId = FindByLongId

data class Notification(
    val type: String,
    val message: String,

    val id: NotificationId? = null,
    val meta: Map<String, Any?> = emptyMap(),
    val ts: Long = System.currentTimeMillis(),
    val read: Boolean = false
)
