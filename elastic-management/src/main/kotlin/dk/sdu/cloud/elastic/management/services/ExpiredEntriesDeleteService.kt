package dk.sdu.cloud.elastic.management.services

import dk.sdu.cloud.service.Loggable
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import java.time.LocalDate
import java.util.*

class ExpiredEntriesDeleteService(
    private val elastic: RestHighLevelClient
) {

    private fun deleteExpired(index: String) {
        val date = Date().time

        val expiredCount = elastic.count(
            CountRequest().source(
                SearchSourceBuilder().query(
                    QueryBuilders.rangeQuery("expiry")
                        .lte(date)
                )
            )
                .indices(index),
            RequestOptions.DEFAULT
        ).count

        val sizeOfIndex = elastic.count(
            CountRequest().source(
                SearchSourceBuilder().query(
                    QueryBuilders.matchAllQuery()
                )
            ).indices(index),
            RequestOptions.DEFAULT
        ).count

        if (expiredCount == 0L) {
            log.info("Nothing expired in index - moving on")
            return
        }

        if (sizeOfIndex == expiredCount) {
            log.info("All doc expired - faster to delete index")
            deleteIndex(index, elastic)
        } else {
            val request = DeleteByQueryRequest(index)
            request.setQuery(
                QueryBuilders.rangeQuery("expiry")
                    .lte(date)
            )

            elastic.deleteByQuery(request, RequestOptions.DEFAULT)
            flushIndex(elastic, index)
        }
    }

    fun deleteOldRancherLogs() {
        val currentdate = LocalDate.now()
        val daysToSave = 180

        val indexToDelete = if (indexExists("development_default-*", elastic))
            "development_default-${currentdate.minusDays(daysToSave.toLong())}"
        else
            "kubernetes-production-${currentdate.minusDays(daysToSave.toLong())}"

        if (!indexExists(indexToDelete, elastic)) {
            log.info("no index with the name $indexToDelete")
            return
        }
        deleteIndex(indexToDelete, elastic)
    }

    fun deleteExpiredAllIndices() {
        val list = getListOfIndices(elastic, "*")
        list.forEach {
            log.info("Finding expired entries in $it")
            deleteExpired(it)
        }
    }

    fun deleteAllEmptyIndices(lowLevelRestClient: RestClient) {
        val list = getListOfIndices(elastic, "*")
        list.forEach {
            if (getDocumentCountSum(listOf(it), lowLevelRestClient) == 0) {
                deleteIndex(it, elastic)
            }
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
