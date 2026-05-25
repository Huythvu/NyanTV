package com.nyantv.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nyantv.ui.utils.NetworkStatusContent
import com.nyantv.ui.widgets.HomeSections
import com.nyantv.viewmodel.AppViewModel

@Composable
fun HomeScreen(vm: AppViewModel, navController: NavController, onDetailClick: (String) -> Unit) {
    val loggedIn     by vm.isLoggedIn.collectAsStateWithLifecycle()
    val profile      by vm.profile.collectAsStateWithLifecycle()
    val networkState by vm.networkState.collectAsStateWithLifecycle()
    val serviceType  by vm.serviceType.collectAsStateWithLifecycle()

    NetworkStatusContent(
        state       = networkState,
        serviceName = serviceType.name.lowercase().replaceFirstChar { it.uppercase() },
        onRetry     = { vm.retryLoad() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text       = "Hey ${if (loggedIn) profile?.name ?: "" else "Guest"}",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text  = "What are we watching today?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            HomeSections(vm = vm, navController = navController, onDetailClick)
        }
    }
}
