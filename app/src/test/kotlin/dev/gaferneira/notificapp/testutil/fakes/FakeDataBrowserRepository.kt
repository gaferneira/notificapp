package dev.gaferneira.notificapp.testutil.fakes

import androidx.paging.PagingData
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.DataStatistics
import dev.gaferneira.notificapp.domain.model.ExportFormat
import dev.gaferneira.notificapp.domain.repository.DataBrowserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.OutputStream

/**
 * Deterministic in-memory [DataBrowserRepository] fake for VM tests. The paginated methods return
 * [pagedRows] (an empty [PagingData] by default) while tracking every call's filter (faking Paging3's
 * real pipeline isn't worth the complexity for VM-level tests, mirroring `FakeNotificationRepository`).
 */
class FakeDataBrowserRepository : DataBrowserRepository {

    val browseCalls = mutableListOf<DataBrowserFilter>()
    val searchCalls = mutableListOf<DataBrowserFilter>()
    val deleteByIdCalls = mutableListOf<String>()
    val deleteByIdsCalls = mutableListOf<List<String>>()
    val exportCalls = mutableListOf<Pair<DataBrowserFilter, ExportFormat>>()

    var pagedRows: PagingData<DataBrowserRow> = PagingData.empty()
    var previewDeleteResult: Result<List<String>> = Result.success(emptyList())
    var deleteByIdResult: Result<Unit> = Result.success(Unit)
    var deleteByIdsResult: Result<Int> = Result.success(0)
    var exportResult: Result<Unit> = Result.success(Unit)

    /** When set, [exportRows] suspends until it completes (lets a test cancel an in-flight export). */
    var exportGate: CompletableDeferred<Unit>? = null

    override fun browsePaged(filter: DataBrowserFilter): Flow<PagingData<DataBrowserRow>> {
        browseCalls += filter
        return flowOf(pagedRows)
    }

    override fun searchPaged(filter: DataBrowserFilter): Flow<PagingData<DataBrowserRow>> {
        searchCalls += filter
        return flowOf(pagedRows)
    }

    override suspend fun statistics(filter: DataBrowserFilter): Result<DataStatistics> = Result.success(
        DataStatistics(total = 0, thisWeek = 0, mostActiveRuleName = null, perRule = emptyList(), perApp = emptyList(), trend = emptyList()),
    )

    override suspend fun exportRows(filter: DataBrowserFilter, sink: OutputStream, format: ExportFormat): Result<Unit> {
        exportCalls += filter to format
        exportGate?.await()
        return exportResult
    }

    override suspend fun previewDelete(filter: DataBrowserFilter): Result<List<String>> = previewDeleteResult

    override suspend fun deleteById(valueId: String): Result<Unit> {
        deleteByIdCalls += valueId
        return deleteByIdResult
    }

    override suspend fun deleteByIds(ids: List<String>): Result<Int> {
        deleteByIdsCalls += ids
        return deleteByIdsResult
    }
}
