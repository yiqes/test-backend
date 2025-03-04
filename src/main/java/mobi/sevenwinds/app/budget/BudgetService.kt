package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val authorEntity = body.authorId?.let { AuthorEntity.findById(it) }
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.author = authorEntity
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val totalQuery = BudgetTable
                //Добавлен фильтр через leftJoin с таблицей AuthorTable.
                .leftJoin(AuthorTable, { authorId }, { id })
                .select { BudgetTable.year eq param.year }
                .apply {
                    param.authorName?.let { name ->
                        andWhere { AuthorTable.name.lowerCase() like "%${name.lowercase()}%" }
                        //Условие like с lowerCase() обеспечивает поиск по подстроке ФИО без учёта регистра
                        //Фильтр применяется к total, items и totalByType

                    }
                }

            val total = totalQuery.count()

            val query = BudgetTable
                .leftJoin(AuthorTable, { authorId }, { id })
                .select { BudgetTable.year eq param.year }
                .apply {
                    param.authorName?.let { name ->
                        andWhere { AuthorTable.name.lowerCase() like "%${name.lowercase()}%" }
                    }
                }
                .orderBy(BudgetTable.month to org.jetbrains.exposed.sql.SortOrder.ASC, BudgetTable.amount to org.jetbrains.exposed.sql.SortOrder.DESC)
                .limit(param.limit, offset = param.offset.toLong())

            val data = BudgetEntity.wrapRows(query).map { it.toResponse() }

            val allDataQuery = BudgetTable
                .leftJoin(AuthorTable, { authorId }, { id })
                .select { BudgetTable.year eq param.year }
                .apply {
                    param.authorName?.let { name ->
                        andWhere { AuthorTable.name.lowerCase() like "%${name.lowercase()}%" }
                    }
                }

            val allData = BudgetEntity.wrapRows(allDataQuery).map { it.toResponse() }
            val sumByType = allData.groupBy { it.type.name }.mapValues { it.value.sumOf { v -> v.amount } }

            return@transaction BudgetYearStatsResponse(
                total = total.toInt(),
                totalByType = sumByType,
                items = data
            )
        }
    }
}