package com.example.data

import android.content.Context
import androidx.room.*

@Dao
interface VibeDao {
    // Projects
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    suspend fun getAllProjects(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE name = :name")
    suspend fun deleteProject(name: String)

    @Query("UPDATE projects SET name = :newName, description = :newDescription WHERE name = :oldName")
    suspend fun updateProject(oldName: String, newName: String, newDescription: String)

    @Query("UPDATE project_files SET projectName = :newName WHERE projectName = :oldName")
    suspend fun updateProjectFilesProjectName(oldName: String, newName: String)

    @Query("UPDATE chat_messages SET projectName = :newName WHERE projectName = :oldName")
    suspend fun updateChatMessagesProjectName(oldName: String, newName: String)

    // Files
    @Query("SELECT * FROM project_files WHERE projectName = :projectName ORDER BY path ASC")
    suspend fun getFilesForProject(projectName: String): List<ProjectFileEntity>

    @Query("SELECT * FROM project_files WHERE projectName = :projectName AND path = :path LIMIT 1")
    suspend fun getFileByPath(projectName: String, path: String): ProjectFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: ProjectFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<ProjectFileEntity>)

    @Update
    suspend fun updateFile(file: ProjectFileEntity)

    @Query("DELETE FROM project_files WHERE projectName = :projectName AND path = :path")
    suspend fun deleteFile(projectName: String, path: String)

    @Query("DELETE FROM project_files WHERE projectName = :projectName")
    suspend fun deleteAllFilesForProject(projectName: String)

    // Chats
    @Query("SELECT * FROM chat_messages WHERE projectName = :projectName ORDER BY timestamp ASC")
    suspend fun getChatsForProject(projectName: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Delete
    suspend fun deleteChatMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE projectName = :projectName")
    suspend fun deleteAllChatsForProject(projectName: String)
}

@Database(
    entities = [ProjectEntity::class, ProjectFileEntity::class, ChatMessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VibeDatabase : RoomDatabase() {
    abstract fun vibeDao(): VibeDao

    companion object {
        @Volatile
        private var INSTANCE: VibeDatabase? = null

        fun getDatabase(context: Context): VibeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VibeDatabase::class.java,
                    "vibe_coder_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
