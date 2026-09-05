package ir.xilo.app.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.RemoteMediator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalPagingApi::class)
class FeedRemoteMediatorTest {

    @Test
    fun append_whenNoMoreFeed_returnsEndOfPagination() = runTest {
        val repository = mockk<PostRepository>()
        every { repository.hasMoreFeed() } returns false

        val mediator = FeedRemoteMediator(repository)
        val result = mediator.load(LoadType.APPEND, mockk(relaxed = true))

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    @Test
    fun prepend_alwaysReturnsEndOfPagination() = runTest {
        val repository = mockk<PostRepository>()
        val mediator = FeedRemoteMediator(repository)

        val result = mediator.load(LoadType.PREPEND, mockk(relaxed = true))

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    @Test
    fun refresh_successUsesHasMoreFeedForEndFlag() = runTest {
        val repository = mockk<PostRepository>()
        coEvery { repository.refreshFeed() } returns Result.success(Unit)
        every { repository.hasMoreFeed() } returns true

        val mediator = FeedRemoteMediator(repository)
        val result = mediator.load(LoadType.REFRESH, mockk(relaxed = true))

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue(!(result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    @Test
    fun refresh_failureReturnsError() = runTest {
        val repository = mockk<PostRepository>()
        coEvery { repository.refreshFeed() } returns Result.failure(IllegalStateException("offline"))

        val mediator = FeedRemoteMediator(repository)
        val result = mediator.load(LoadType.REFRESH, mockk(relaxed = true))

        assertTrue(result is RemoteMediator.MediatorResult.Error)
    }
}
