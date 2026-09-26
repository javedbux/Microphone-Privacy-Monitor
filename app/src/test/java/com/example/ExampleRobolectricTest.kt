package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.AccessEvent
import com.example.data.model.ResourceType
import com.example.data.model.RiskLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context matches app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Privacy Guard", appName)
    }

    @Test
    fun `insert and retrieve access event`() = runBlocking {
        val event = AccessEvent(
            appName = "Test App",
            packageName = "com.test.app",
            resourceType = ResourceType.MICROPHONE,
            durationSeconds = 10,
            isBackground = true,
            riskLevel = RiskLevel.WORTH_CHECKING,
            notes = "Test background recording"
        )
        db.accessEventDao().insertEvent(event)

        val events = db.accessEventDao().getAllEvents().first()
        assertEquals(1, events.size)
        assertEquals("Test App", events[0].appName)
        assertEquals(ResourceType.MICROPHONE, events[0].resourceType)
        assertTrue(events[0].isBackground)
        assertEquals(RiskLevel.WORTH_CHECKING, events[0].riskLevel)
    }
}
