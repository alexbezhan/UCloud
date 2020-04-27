package dk.sdu.cloud.app.store.api

interface NameAndVersion {
    val name: String
    val version: String
}

data class NameAndVersionImpl(override val name: String, override val version: String) : NameAndVersion {

    override fun toString() = "$name@$version"
}

@Suppress("FunctionNaming")
fun NameAndVersion(name: String, version: String): NameAndVersion = NameAndVersionImpl(name, version)
