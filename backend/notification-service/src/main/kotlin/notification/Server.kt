package dk.sdu.cloud.notification

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.serviceInstance
import dk.sdu.cloud.notification.http.NotificationController
import dk.sdu.cloud.notification.services.NotificationDao
import dk.sdu.cloud.notification.services.NotificationService
import dk.sdu.cloud.notification.services.SubscriptionDao
import dk.sdu.cloud.notification.services.SubscriptionService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices
import org.slf4j.Logger

class Server(override val micro: Micro) : CommonServer {
    override val log: Logger = logger()
    private lateinit var subscriptionService: SubscriptionService
    private lateinit var notificationService: NotificationService

    override fun start() {
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        log.info("Creating core services")
        val notificationDao = NotificationDao()
        val localhost = run {
            val ip = micro.serviceInstance.ipAddress
                ?: throw IllegalStateException("micro.serviceInstance.ipAddress == null")

            val port = micro.serviceInstance.port

            HostInfo(ip, port = port)
        }
        val wsClient = micro.authenticator.authenticateClient(OutgoingWSCall)
        subscriptionService = SubscriptionService(localhost, wsClient, db, SubscriptionDao())
        notificationService = NotificationService(db, notificationDao, subscriptionService)

        log.info("Core services constructed!")


        with(micro.server) {
            configureControllers(
                NotificationController(
                    notificationService,
                    subscriptionService
                )
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        subscriptionService.close()
    }
}
