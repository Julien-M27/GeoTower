package fr.geotower.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GeoTowerPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val cappedIndicatorState = remember(pullToRefreshState) {
        CappedPullToRefreshState(pullToRefreshState)
    }
    val contentTopPadding = PullToRefreshDefaults.PositionalThreshold *
        if (isRefreshing) 1f else pullToRefreshState.distanceFraction.coerceIn(0f, 1f)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = pullToRefreshState,
        enabled = enabled,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = cappedIndicatorState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentTopPadding)
        ) {
            content()
        }
    }
}

private class CappedPullToRefreshState(
    private val delegate: PullToRefreshState
) : PullToRefreshState {
    override val distanceFraction: Float
        get() = delegate.distanceFraction.coerceIn(0f, 1f)

    override val isAnimating: Boolean
        get() = delegate.isAnimating

    override suspend fun animateToThreshold() {
        delegate.animateToThreshold()
    }

    override suspend fun animateToHidden() {
        delegate.animateToHidden()
    }

    override suspend fun snapTo(targetValue: Float) {
        delegate.snapTo(targetValue)
    }
}
