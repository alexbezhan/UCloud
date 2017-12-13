package org.esciencecloud.auth

data class Service(val name: String, val endpoint: String)

object ServiceDAO {
    private val inMemoryDb = HashMap<String, Service>()

    init {
        insert(Service("local-dev", "http://localhost:9000/auth"))
    }

    fun insert(service: Service): Boolean {
        if (service.name !in inMemoryDb) {
            inMemoryDb[service.name] = service
            return true
        }
        return false
    }

    fun findByName(name: String): Service? {
        return inMemoryDb[name]
    }
}

