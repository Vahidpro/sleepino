package pasac.sleepino.di
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import pasac.sleepino.data.*
import java.time.Clock
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class) object AppModule {
 @Provides @Singleton fun database(@ApplicationContext context: Context): SleepDatabase = Room.databaseBuilder(context,SleepDatabase::class.java,"sleep-time.db").build()
 @Provides fun history(db: SleepDatabase) = db.history()
 @Provides @Singleton fun clock(): Clock = Clock.systemUTC()
}
