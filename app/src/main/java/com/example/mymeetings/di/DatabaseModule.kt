package com.example.mymeetings.di

import android.content.Context
import androidx.room.Room
import com.example.mymeetings.data.local.DatabaseKeyProvider
import com.example.mymeetings.data.local.MeetingDao
import com.example.mymeetings.data.local.MeetingDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeetingDatabase {
        val passphrase = DatabaseKeyProvider.getOrGeneratePassphrase(context)
        val factory = SupportFactory(passphrase)
        
        return Room.databaseBuilder(
            context,
            MeetingDatabase::class.java,
            "mymeetings.db"
        )
        .openHelperFactory(factory)
        .build()
    }

    @Provides
    @Singleton
    fun provideMeetingDao(database: MeetingDatabase): MeetingDao {
        return database.meetingDao()
    }
}
