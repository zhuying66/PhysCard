package com.physcard.physics.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FormulaDao {
    @Query("SELECT * FROM formulas ORDER BY orderIndex, title")
    fun observeAll(): Flow<List<FormulaEntity>>

    @Query("SELECT * FROM formulas ORDER BY orderIndex, title")
    suspend fun getAll(): List<FormulaEntity>

    @Query("SELECT * FROM formulas WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FormulaEntity?

    @Query("SELECT COUNT(*) FROM formulas")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(formulas: List<FormulaEntity>)

    @Query("DELETE FROM formulas")
    suspend fun deleteAll()
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions ORDER BY formulaId, ability, id")
    fun observeAll(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions ORDER BY formulaId, ability, id")
    suspend fun getAll(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE formulaId = :formulaId AND ability = :ability ORDER BY id")
    suspend fun getForFormulaAndAbility(formulaId: String, ability: String): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE formulaId = :formulaId")
    suspend fun getForFormula(formulaId: String): List<QuestionEntity>

    @Query("SELECT * FROM question_options ORDER BY questionId, orderIndex")
    suspend fun getAllOptions(): List<QuestionOptionEntity>

    @Query("SELECT * FROM question_answers")
    suspend fun getAllAnswers(): List<QuestionAnswerEntity>

    @Query("SELECT * FROM question_options WHERE questionId = :questionId ORDER BY orderIndex")
    suspend fun getOptions(questionId: String): List<QuestionOptionEntity>

    @Query("SELECT * FROM question_answers WHERE questionId = :questionId LIMIT 1")
    suspend fun getAnswer(questionId: String): QuestionAnswerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestions(questions: List<QuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOptions(options: List<QuestionOptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnswers(answers: List<QuestionAnswerEntity>)

    @Query("DELETE FROM questions WHERE id IN (:ids)")
    suspend fun deleteQuestions(ids: List<String>)

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun count(): Int

    @Query("SELECT DISTINCT ability FROM questions WHERE formulaId = :formulaId")
    suspend fun abilitiesForFormula(formulaId: String): List<String>

    @Query("SELECT DISTINCT formulaId FROM questions")
    suspend fun formulaIdsWithQuestions(): List<String>
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_states")
    fun observeAllStates(): Flow<List<ReviewStateEntity>>

    @Query("SELECT * FROM review_states")
    suspend fun getAllStates(): List<ReviewStateEntity>

    @Query(
        "SELECT * FROM review_states " +
            "WHERE introduced = 1 AND dueAt IS NOT NULL AND dueAt <= :now " +
            "ORDER BY dueAt ASC",
    )
    suspend fun getDueStates(now: Long): List<ReviewStateEntity>

    @Query("SELECT * FROM review_states WHERE formulaId = :formulaId")
    suspend fun getStatesForFormula(formulaId: String): List<ReviewStateEntity>

    @Query("SELECT * FROM review_states WHERE cardKey = :cardKey LIMIT 1")
    suspend fun getState(cardKey: String): ReviewStateEntity?

    @Query("SELECT * FROM review_states WHERE introduced = 0")
    suspend fun getUnintroducedStates(): List<ReviewStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStates(states: List<ReviewStateEntity>)

    @Update
    suspend fun updateState(state: ReviewStateEntity)

    @Query("SELECT COUNT(*) FROM review_states WHERE formulaId = :formulaId AND reviewCount > 0")
    suspend fun reviewedAbilityCount(formulaId: String): Int

    @Insert
    suspend fun insertLog(log: ReviewLogEntity): Long

    @Query("SELECT * FROM review_logs ORDER BY reviewedAt DESC")
    fun observeAllLogs(): Flow<List<ReviewLogEntity>>

    @Query("SELECT * FROM review_logs ORDER BY reviewedAt DESC")
    suspend fun getAllLogs(): List<ReviewLogEntity>

    @Query("SELECT * FROM review_logs WHERE cardKey = :cardKey ORDER BY reviewedAt DESC")
    suspend fun getLogsForCard(cardKey: String): List<ReviewLogEntity>

    @Query("SELECT COUNT(*) FROM review_logs WHERE localDate = :localDate")
    suspend fun countLogsOn(localDate: String): Int

    @Query("DELETE FROM review_logs")
    suspend fun deleteAllLogs()

    @Query("DELETE FROM review_states")
    suspend fun deleteAllStates()
}
