package dev.gaferneira.notificapp.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.gaferneira.notificapp.core.data.local.entity.SuggestionDismissalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for suggestion dismissals.
 */
@Dao
internal interface SuggestionDismissalDao {

    /**
     * Observe every dismissal as a Flow, for the ViewModel's suggestion-filtering combine.
     */
    @Query("SELECT * FROM suggestion_dismissals")
    fun observeAll(): Flow<List<SuggestionDismissalEntity>>

    /**
     * Record a dismissal. Replaces on conflict - re-dismissing the same (package_name,
     * normalized_title_key) key refreshes dismissed_at rather than creating a duplicate row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun dismiss(entity: SuggestionDismissalEntity)
}
