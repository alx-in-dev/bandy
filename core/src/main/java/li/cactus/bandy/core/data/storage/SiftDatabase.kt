package li.cactus.bandy.core.data.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RecordingEntity::class], version = 1, exportSchema = false)
abstract class SiftDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
}
