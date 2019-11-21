package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.app.license.api.Application
import dk.sdu.cloud.app.license.api.ApplicationLicenseServer
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.app.license.services.acl.UserEntity

interface AppLicenseDao<Session> {
    fun getById(
        session: Session,
        id: String
    ): LicenseServerEntity?

    fun create(
        session: Session,
        appLicenseServer: ApplicationLicenseServer
    ) : String

    fun addApplicationToServer(
        session: Session,
        application: Application,
        serverId: String
    )

    fun removeApplicationFromServer(
        session: Session,
        application: Application,
        serverId: String
    )

    fun list(
        session: Session,
        application: Application,
        entity: UserEntity
    ) : List<LicenseServerEntity>?

    fun save(
        session: Session,
        appLicenseServer: ApplicationLicenseServer,
        withId: String
    )
}