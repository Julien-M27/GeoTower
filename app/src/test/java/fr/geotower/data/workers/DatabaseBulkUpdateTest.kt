package fr.geotower.data.workers

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseBulkUpdateTest {
    @Test
    fun independentChecks_runConcurrently() = runBlocking {
        val started = CountDownLatch(3)
        val release = CountDownLatch(1)
        val activeChecks = AtomicInteger(0)
        val maximumActiveChecks = AtomicInteger(0)

        val result = async(Dispatchers.Default) {
            DatabaseBulkUpdate.runChecksConcurrently(
                listOf(1, 2, 3).map { expected ->
                    suspend {
                        started.countDown()
                        check(started.await(2, TimeUnit.SECONDS))
                        val active = activeChecks.incrementAndGet()
                        maximumActiveChecks.updateAndGet { current -> maxOf(current, active) }
                        check(release.await(2, TimeUnit.SECONDS))
                        activeChecks.decrementAndGet()
                        expected
                    }
                }
            )
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        release.countDown()

        assertEquals(listOf(1, 2, 3), result.await())
        assertEquals(3, maximumActiveChecks.get())
    }
}
