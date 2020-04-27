package dk.sdu.cloud.service.db

import eu.infomas.annotation.AnnotationDetector
import org.hibernate.SessionFactory
import org.hibernate.StatelessSession
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.PostgreSQL95Dialect
import org.slf4j.LoggerFactory
import java.sql.Types
import javax.persistence.Entity

typealias HibernateSession = org.hibernate.Session

class HibernateSessionFactory(
    @PublishedApi
    internal val factory: SessionFactory,

    @PublishedApi
    internal val metadata: Metadata,

    val autoDetectedEntities: List<Class<*>> = emptyList()
) : DBSessionFactory<HibernateSession> {
    override suspend fun openSession(): HibernateSession {
        return factory.openSession()
    }

    override suspend fun closeSession(session: HibernateSession) {
        session.close()
    }

    override suspend fun openTransaction(session: HibernateSession) {
        session.beginTransaction()
    }

    override suspend fun commit(session: HibernateSession) {
        session.transaction.commit()
    }

    override suspend fun close() {
        factory.close()
    }

    override suspend fun flush(session: HibernateSession) {
        session.flush()
    }

    fun openStatelessSession(): StatelessSession {
        return factory.openStatelessSession()
    }

    companion object {
        private val log = LoggerFactory.getLogger(HibernateSessionFactory::class.java)

        fun create(config: HibernateDatabaseConfig? = null): HibernateSessionFactory {
            val registry = StandardServiceRegistryBuilder().apply {
                if (config?.skipXml != true) {
                    configure()
                }

                if (config == null) return@apply

                with(config) {
                    if (driver != null) applySetting("hibernate.connection.driver_class", driver)
                    if (jdbcUrl != null) applySetting("hibernate.connection.url", jdbcUrl)
                    if (username != null) applySetting("hibernate.connection.username", username)
                    applySetting("hibernate.connection.password", password ?: "")

                    if (dialect != null) applySetting("hibernate.dialect", dialect)
                    if (showSQLInStdout) applySetting("hibernate.show_sql", true.toString())
                    if (recreateSchemaOnStartup) {
                        applySetting("hibernate.hbm2ddl.auto", "create")
                    } else {
                        if (validateSchemaOnStartup) applySetting("hibernate.hbm2ddl.auto", "validate")
                    }

                    applySetting("hibernate.default_schema", config.defaultSchema)
                    applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                    if (usePool) applySetting(
                        "hibernate.connection.provider_class",
                        "org.hibernate.hikaricp.internal.HikariCPConnectionProvider"
                    )

                    applySetting(
                        "hibernate.physical_naming_strategy",
                        SnakeCasePhysicalNamingStrategy::class.qualifiedName
                    )

                    if (poolSize != null) {
                        applySetting("hibernate.hikari.minimumIdle", "1")
                        applySetting("hibernate.hikari.maximumPoolSize", poolSize.toString())
                    }

                    applySetting("hibernate.hikari.connectionTimeout", "20000")
                    applySetting("hibernate.hikari.idleTimeout", "300000")
                    applySetting("hibernate.hikari.maxLifetime", "600000")

                    if (skipXml && !autoDetectEntities) {
                        log.warn("Skipping XML configuration but also not auto detecting entities")
                    }
                }
            }.build()

            val entities = if (config?.autoDetectEntities == true) {
                detectEntities(*config.detectEntitiesInPackages.toTypedArray())
            } else {
                emptyList()
            }

            @Suppress("TooGenericExceptionCaught")
            return (try {
                MetadataSources(registry).apply {
                    metadataBuilder.applyBasicType(JsonbType(), "jsonb")
                    metadataBuilder.applyBasicType(JsonbCollectionType(), "jsonb")
                    metadataBuilder.applyBasicType(JsonbMapType(), "jsonb")

                    entities.forEach { addAnnotatedClass(it) }
                }.buildMetadata()
            } catch (ex: Exception) {
                StandardServiceRegistryBuilder.destroy(registry)
                throw ex
            }).let { HibernateSessionFactory(it.buildSessionFactory(), it, entities) }
        }
    }
}

data class HibernateDatabaseConfig(
    val driver: String?,
    val jdbcUrl: String?,
    val dialect: String?,
    val username: String?,
    val password: String?,
    val usePool: Boolean = true,
    val poolSize: Int? = 50,
    val defaultSchema: String = "public",
    val skipXml: Boolean = true,
    val showSQLInStdout: Boolean = false,
    val recreateSchemaOnStartup: Boolean = false,
    val validateSchemaOnStartup: Boolean = false,
    val autoDetectEntities: Boolean = true,
    val detectEntitiesInPackages: List<String> = listOf("dk.sdu.cloud")
)

const val H2_DRIVER = "org.h2.Driver"
const val H2_TEST_JDBC_URL = "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE"
const val H2_DIALECT = "dk.sdu.cloud.service.db.H2DialectWithJson"

class H2DialectWithJson : H2Dialect() {
    init {
        registerColumnType(Types.JAVA_OBJECT, "varchar(${Int.MAX_VALUE})")
    }
}

class PostgresDialectWithJson : PostgreSQL95Dialect() {
    init {
        registerColumnType(Types.JAVA_OBJECT, "jsonb")
    }
}

val H2_TEST_CONFIG = HibernateDatabaseConfig(
    driver = H2_DRIVER,
    jdbcUrl = H2_TEST_JDBC_URL,
    dialect = H2_DIALECT,
    username = "sa",
    password = "",
    usePool = false,
    poolSize = 1,
    recreateSchemaOnStartup = true
)

const val POSTGRES_DRIVER = "org.postgresql.Driver"
const val POSTGRES_9_5_DIALECT = "dk.sdu.cloud.service.db.PostgresDialectWithJson"

fun postgresJdbcUrl(host: String, database: String, port: Int? = null): String {
    return StringBuilder().apply {
        append("jdbc:postgresql://")
        append(host)
        if (port != null) {
            append(':')
            append(port)
        }
        append('/')
        append(database)
    }.toString()
}

private fun detectEntities(vararg where: String): List<Class<*>> {
    val entities = mutableListOf<Class<*>>()
    AnnotationDetector(object : AnnotationDetector.TypeReporter {
        override fun reportTypeAnnotation(annotation: Class<out Annotation>?, className: String?) {
            entities.add(Class.forName(className))
        }

        override fun annotations(): Array<out Class<out Annotation>> = arrayOf(Entity::class.java)
    }).detect(*where)
    return entities
}
