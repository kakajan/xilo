package ir.xilo.app.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import ir.xilo.app.data.local.entity.PostEntity

@OptIn(ExperimentalPagingApi::class)
class FeedRemoteMediator(
    private val postRepository: PostRepository,
) : RemoteMediator<Int, PostEntity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PostEntity>,
    ): MediatorResult {
        return try {
            when (loadType) {
                LoadType.REFRESH -> {
                    postRepository.refreshFeed().fold(
                        onSuccess = {
                            MediatorResult.Success(
                                endOfPaginationReached = !postRepository.hasMoreFeed(),
                            )
                        },
                        onFailure = { MediatorResult.Error(it) },
                    )
                }
                LoadType.APPEND -> {
                    if (!postRepository.hasMoreFeed()) {
                        MediatorResult.Success(endOfPaginationReached = true)
                    } else {
                        postRepository.loadMoreFeed().fold(
                            onSuccess = {
                                MediatorResult.Success(
                                    endOfPaginationReached = !postRepository.hasMoreFeed(),
                                )
                            },
                            onFailure = { MediatorResult.Error(it) },
                        )
                    }
                }
                LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)
            }
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
