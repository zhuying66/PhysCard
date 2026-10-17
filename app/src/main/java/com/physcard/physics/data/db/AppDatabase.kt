package com.physcard.physics.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FormulaEntity::class,
        QuestionEntity::class,
        QuestionOptionEntity::class,
        QuestionAnswerEntity::class,
        ReviewStateEntity::class,
        ReviewLogEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun formulaDao(): FormulaDao
    abstract fun questionDao(): QuestionDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        const val VERSION = 2
        const val NAME = "physics_formula_memory.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_review_states_introduced_dueAt " +
                        "ON review_states(introduced, dueAt)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_review_states_formulaId_dueAt " +
                        "ON review_states(formulaId, dueAt)",
                )
            }
        }

        /**
         * Every schema change ships with an explicit migration here. Destructive fallback is
         * intentionally disabled: losing a student's review history because of a schema bump is
         * worse than showing a clear startup error.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
