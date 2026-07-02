package com.trekmate.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trekmate.app.feature.tour.TourUiState
import com.trekmate.app.feature.tour.TourViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinTourScreen(
    onBack: () -> Unit,
    onTourJoined: () -> Unit,
    tourViewModel: TourViewModel = hiltViewModel()
) {
    val uiState by tourViewModel.uiState.collectAsState()
    var joinCode by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is TourUiState.Active) onTourJoined()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Tour") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Enter the join code shared by the tour leader.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it.uppercase() },
                label = { Text("Join Code") },
                placeholder = { Text("e.g. ABC123") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (joinCode.isNotBlank()) tourViewModel.joinTour(joinCode) }
                ),
                modifier = Modifier.fillMaxWidth(),
                isError = uiState is TourUiState.Error
            )

            if (uiState is TourUiState.Error) {
                Text(
                    (uiState as TourUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { tourViewModel.joinTour(joinCode) },
                enabled = joinCode.isNotBlank() && uiState !is TourUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is TourUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Join Tour")
                }
            }
        }
    }
}
