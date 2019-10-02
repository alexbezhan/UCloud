package dk.sdu.cloud.micro

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import java.io.File

sealed class ServerConfigurationException(why: String, status: HttpStatusCode) : RPCException(why, status) {
    class MissingNode(path: String) :
        ServerConfigurationException("Missing configuration at $path", HttpStatusCode.InternalServerError)
}

class ServerConfiguration internal constructor(
    private val jsonMapper: ObjectMapper,
    internal val tree: JsonNode
) {
    fun <T> requestChunk(valueTypeRef: TypeReference<T>, node: String): T {
        val jsonNode =
            tree.get(node)?.takeIf { !it.isMissingNode }
                ?: throw ServerConfigurationException.MissingNode(node)
        return jsonNode.traverse(jsonMapper).readValueAs<T>(valueTypeRef)
    }

    fun <T> requestChunkAt(valueTypeRef: TypeReference<T>, vararg path: String): T {
        val jsonNode = tree.at("/" + path.joinToString("/"))?.takeIf { !it.isMissingNode }
            ?: throw ServerConfigurationException.MissingNode(path.joinToString("/"))
        return jsonNode.traverse(jsonMapper).readValueAs<T>(valueTypeRef)
    }

    inline fun <reified T : Any> requestChunk(node: String): T {
        return requestChunk(jacksonTypeRef(), node)
    }

    inline fun <reified T : Any> requestChunkAt(vararg path: String): T {
        return requestChunkAt(jacksonTypeRef(), *path)
    }

    inline fun <reified T : Any> requestChunkOrNull(node: String): T? {
        return try {
            requestChunk(node)
        } catch (ex: ServerConfigurationException.MissingNode) {
            null
        }
    }

    inline fun <reified T : Any> requestChunkAtOrNull(vararg path: String): T? {
        return try {
            requestChunkAt(*path)
        } catch (ex: ServerConfigurationException.MissingNode) {
            null
        }
    }
}

class ConfigurationFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        log.info("Reading configuration...")

        val initialConfigFile =
            cliArgs.getOrNull(0)?.takeIf { !it.startsWith("--") }?.let { File(it) }

        val allConfigFiles = ArrayList<File>()
        if (initialConfigFile != null) allConfigFiles.add(initialConfigFile)

        val argIterator = cliArgs.iterator()
        while (argIterator.hasNext()) {
            val arg = argIterator.next()

            if (arg == "--config") {
                val configFile = if (argIterator.hasNext()) argIterator.next() else null
                if (configFile == null) {
                    log.info("Dangling --config. Correct syntax is --config <file>")
                } else {
                    allConfigFiles.add(File(configFile))
                }
            } else if (arg == "--config-dir") {
                val configDirectory = (if (argIterator.hasNext()) argIterator.next() else null)?.let { File(it) }
                if (configDirectory == null) {
                    log.info("Dangling --config-dir. Correct syntax is --config-dir <directory>")
                } else {
                    if (configDirectory.exists() && configDirectory.isDirectory) {
                        allConfigFiles.addAll(
                            configDirectory.listFiles()?.filter { it.extension in knownExtensions } ?: emptyList()
                        )
                    }
                }
            }
        }

        val tree = jsonMapper.readTree("{}")
        val serverConfiguration = ServerConfiguration(jsonMapper, tree)
        for (configFile in allConfigFiles) {
            injectFile(serverConfiguration, configFile)
        }

        // NOTE: This is not meant to be recursive.
        val additionalDirs =
            serverConfiguration.requestChunkAtOrNull<List<String>>("config", "additionalDirectories") ?: emptyList()
        for (additionalDir in additionalDirs) {
            for (file in (File(additionalDir).listFiles() ?: emptyArray())) {
                if (file.extension !in knownExtensions) continue
                injectFile(serverConfiguration, file)
            }
        }

        ctx.configuration = serverConfiguration
    }

    fun injectFile(configuration: ServerConfiguration, configFile: File) {
        log.debug("Reading from configuration file: ${configFile.absolutePath}")

        if (!configFile.exists()) {
            log.info("Could not find configuration file: ${configFile.absolutePath}")
            return
        }

        val mapper = when (configFile.extension) {
            "yml", "yaml" -> yamlMapper
            "json" -> jsonMapper
            else -> jsonMapper
        }

        configuration.tree.mergeWith(mapper.readTree(configFile))
    }

    fun manuallyInjectNode(configuration: ServerConfiguration, node: JsonNode) {
        log.debug("Manually injecting node into configuration")
        configuration.tree.mergeWith(node)
    }

    private fun JsonNode.mergeWith(updateNode: JsonNode) {
        val mainNode: JsonNode = this

        updateNode.fieldNames().forEach { fieldName ->
            val mainJsonNode: JsonNode? = mainNode.get(fieldName)
            val updateJsonNode: JsonNode? = updateNode.get(fieldName)

            when {
                mainNode is ObjectNode &&
                        mainJsonNode?.isObject == true &&
                        updateJsonNode?.isObject == false -> {
                    mainNode.set(fieldName, updateJsonNode)
                }

                mainJsonNode?.isObject == true -> {
                    mainJsonNode.mergeWith(updateNode.get(fieldName))
                }

                mainJsonNode is ArrayNode && updateJsonNode is ArrayNode -> {
                    mainJsonNode.addAll(updateJsonNode)
                }

                mainNode is ObjectNode -> {
                    mainNode.set(fieldName, updateJsonNode)
                }
            }
        }
    }

    companion object Feature : MicroFeatureFactory<ConfigurationFeature, Unit>,
        Loggable {
        override val key: MicroAttributeKey<ConfigurationFeature> =
            MicroAttributeKey("configuration-feature")

        override fun create(config: Unit): ConfigurationFeature =
            ConfigurationFeature()

        override val log = logger()

        private val jsonMapper = ObjectMapper().apply { basicConfig() }
        private val yamlMapper = ObjectMapper(YAMLFactory()).apply { basicConfig() }

        private fun ObjectMapper.basicConfig() {
            registerKotlinModule()
            configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            configure(JsonParser.Feature.ALLOW_MISSING_VALUES, true)
            configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
        }

        private val knownExtensions = listOf("yml", "yaml", "json")

        internal val CONFIG_KEY = MicroAttributeKey<ServerConfiguration>("server-configuration")
    }
}

var Micro.configuration: ServerConfiguration
    get() = attributes[ConfigurationFeature.CONFIG_KEY]
    internal set(value) {
        attributes[ConfigurationFeature.CONFIG_KEY] = value
    }

