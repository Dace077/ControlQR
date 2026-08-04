package com.controlqr.acceso.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/** Los enums se guardan por nombre para que las consultas SQL sigan siendo legibles. */
class Converters {
    @TypeConverter fun roleToString(value: UserRole): String = value.name
    @TypeConverter fun stringToRole(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter fun statusToString(value: PassStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): PassStatus = PassStatus.valueOf(value)

    @TypeConverter fun originToString(value: PassOrigin): String = value.name
    @TypeConverter fun stringToOrigin(value: String): PassOrigin = PassOrigin.valueOf(value)

    @TypeConverter fun scanTypeToString(value: ScanType): String = value.name
    @TypeConverter fun stringToScanType(value: String): ScanType = ScanType.valueOf(value)
}

@Database(
    entities = [UserEntity::class, PassEntity::class, ScanEventEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun passDao(): PassDao
    abstract fun scanEventDao(): ScanEventDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "controlqr.db"
            ).build().also { instance = it }
        }
    }
}
