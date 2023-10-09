package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorEntity
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.authorId = body.authorId
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val authorFIO = param.authorFIO

            val query = BudgetTable.select { BudgetTable.year eq param.year }
            val data = getFilteredData(query, authorFIO)
            val total = data.count()

            val sumByType = data.groupBy { it.type.name }
                .mapValues { it.value.sumOf { v -> v.amount } }

            val limitQuery = query.limit(param.limit, param.offset)
                .orderBy(BudgetTable.month to SortOrder.ASC)
                .orderBy(BudgetTable.amount to SortOrder.DESC)

            val limitData = getFilteredData(limitQuery, authorFIO)


            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = limitData
            )
        }
    }
    private fun getFilteredData(query: Query, authorFIO: String?) : List<BudgetRecordWithAuthor> {

        val data = BudgetEntity.wrapRows(query).map { it.toResponse() }.map { budgetRecord ->
            val authorId =  budgetRecord.authorId

            if (authorId == null) {
                return@map BudgetRecordWithAuthor(budgetRecord)
            } else {
                val authorQuery = AuthorTable.select { AuthorTable.id eq authorId }
                val authorEntity = AuthorEntity.wrapRow(authorQuery.first())

                val fullAuthorName = authorEntity.fio
                val authorCreated = authorEntity.createTime

                val formatter: DateTimeFormatter = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss")
                val authorCreatTimeString = formatter.print(authorCreated)

                return@map BudgetRecordWithAuthor(budgetRecord, fullAuthorName, authorCreatTimeString)
            }
        }
        return if (authorFIO == null) {
            data
        } else {
            data.filter { budgetRecordWithAuthor ->
                return@filter budgetRecordWithAuthor.authorFIO?.contains(authorFIO, ignoreCase = true) == true
            }
        }
    }
}