package fr.geotower.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberNetworkConnectivityState(): State<Boolean> {
    return rememberNetworkConnectivityState(LocalContext.current)
}

@Composable
fun rememberNetworkConnectivityState(context: Context): State<Boolean> {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val isOnlineState = remember(context) { mutableStateOf(isNetworkAvailable(context)) }

    DisposableEffect(connectivityManager, context) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnlineState.value = isNetworkAvailable(context)
            }

            override fun onLost(network: Network) {
                isOnlineState.value = isNetworkAvailable(context)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                isOnlineState.value = hasInternetTransport(networkCapabilities)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    return isOnlineState
}

@Composable
fun rememberNetworkAvailableState(): Boolean {
    return rememberNetworkAvailableState(LocalContext.current)
}

@Composable
fun rememberNetworkAvailableState(context: Context): Boolean {
    return rememberNetworkConnectivityState(context).value
}

fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return hasInternetTransport(capabilities)
}

private fun hasInternetTransport(capabilities: NetworkCapabilities): Boolean {
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
}
