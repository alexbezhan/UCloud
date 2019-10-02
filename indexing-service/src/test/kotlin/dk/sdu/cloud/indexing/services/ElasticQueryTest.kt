package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.ownerName
import dk.sdu.cloud.indexing.utils.elasticFile
import dk.sdu.cloud.indexing.utils.eventMatStorFile
import dk.sdu.cloud.indexing.utils.fileQuery
import dk.sdu.cloud.indexing.utils.minimumStatisticRequest
import dk.sdu.cloud.service.NormalizedPaginationRequest
import io.mockk.every
import io.mockk.mockk
import org.apache.lucene.search.TotalHits
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val NUMBER_OF_HITS = 20

class ElasticQueryTest {

    private val client = mockk<RestHighLevelClient>()
    private val elastic = ElasticQueryService(client)


    private fun mockElasticSearchReponseTwentyHits(client: RestHighLevelClient) {
        every { client.search(any(), RequestOptions.DEFAULT) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = SearchHits(
                    Array(NUMBER_OF_HITS) { _ -> SearchHit(2) },
                    TotalHits(NUMBER_OF_HITS.toLong(), TotalHits.Relation.EQUAL_TO),
                    1.9f
                )
                hits
            }
            response
        }
    }

    @Test
    fun `Find Query Test`() {
        every { client.get(any(), any()) } answers {
            val response = mockk<GetResponse>()
            every { response.isExists } returns true
            every { response.sourceAsString } returns
                    defaultMapper.writeValueAsString(elasticFile)
            response
        }
        val result = elastic.findFileByIdOrNull("1")
        assertEquals(eventMatStorFile.ownerName, result?.ownerName)


    }

    @Test
    fun `Find Query Test - does not exist`() {
        every { client.get(any(), any()) } answers {
            val response = mockk<GetResponse>()
            every { response.isExists } returns false
            response
        }
        assertNull(elastic.findFileByIdOrNull("1"))
    }

    //TODO Does not return correctly but does give CC. Error with response.hits
    @Test
    fun `Reverse lookup batch Test`() {
        mockElasticSearchReponseTwentyHits(client)
        val lookupList = List(20) { i -> i.toString() }
        val result = elastic.reverseLookupBatch(lookupList)

        println(result)
    }

    @Test(expected = RPCException::class)
    fun `Reverse lookup batch Test - To many ids`() {
        mockElasticSearchReponseTwentyHits(client)
        val lookupList = List(110) { i -> i.toString() }
        val result = elastic.reverseLookupBatch(lookupList)

        println(result)
    }

    @Test(expected = RPCException::class)
    fun `Reverse lookup Test - not found`() {
        mockElasticSearchReponseTwentyHits(client)
        elastic.reverseLookup("1")
    }

    @Test
    fun `Simple Query Test`() {
        mockElasticSearchReponseTwentyHits(client)
        val queryResults = elastic.query(fileQuery, NormalizedPaginationRequest(25, 0))
        assertEquals(NUMBER_OF_HITS, queryResults.itemsInTotal)
    }

    @Test
    fun `Simple Query Test - empty string`() {
        mockElasticSearchReponseTwentyHits(client)
        val queryResults = elastic.query(fileQuery.copy(fileNameQuery = listOf("")), NormalizedPaginationRequest(25, 0))
        assertEquals(NUMBER_OF_HITS, queryResults.itemsInTotal)
    }

    @Test
    fun `statistics minimal query test`() {
        mockElasticSearchReponseTwentyHits(client)
        val statisticResults = elastic.statisticsQuery(minimumStatisticRequest)
        assertEquals(NUMBER_OF_HITS, statisticResults.count.toInt())
    }
}
