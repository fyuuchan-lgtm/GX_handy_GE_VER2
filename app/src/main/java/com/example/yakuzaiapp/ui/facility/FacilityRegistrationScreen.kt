package com.example.yakuzaiapp.ui.facility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yakuzaiapp.R
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.repository.FacilityRepository
import com.example.yakuzaiapp.data.repository.POSTAL_ERROR_INVALID_POSTAL_CODE
import com.example.yakuzaiapp.data.repository.POSTAL_ERROR_SEARCH_FAILED
import com.example.yakuzaiapp.data.repository.PostalAddress
import com.example.yakuzaiapp.data.repository.PostalCodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacilityRegistrationScreen(
    onBack: () -> Unit,
    viewModel: FacilityRegistrationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = FacilityRegistrationViewModel.Factory
    )
) {
    val facility by viewModel.facility.collectAsStateWithLifecycle()
    val postalSearchState by viewModel.postalSearchState.collectAsStateWithLifecycle()
    var facilityName by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var prefecture by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var town by remember { mutableStateOf("") }
    var streetAddress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val requiredMessage = stringResource(R.string.facility_required)
    val savedMessage = stringResource(R.string.facility_saved)

    LaunchedEffect(facility) {
        facilityName = facility.name
        postalCode = facility.postalCode
        prefecture = facility.prefecture
        city = facility.city
        town = facility.town
        streetAddress = facility.streetAddress
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.facility_registration_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.scan_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = facilityName,
                onValueChange = { facilityName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.facility_name_label)) },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = postalCode,
                    onValueChange = { postalCode = it.filter { char -> char.isDigit() || char == '-' } },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.facility_postal_code_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedButton(
                    onClick = {
                        message = null
                        viewModel.searchPostalCode(postalCode)
                    }
                ) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                    Text(stringResource(R.string.facility_search_address))
                }
            }
            PostalSearchMessage(
                state = postalSearchState,
                onAddressSelected = { address ->
                    postalCode = address.postalCode
                    prefecture = address.prefecture
                    city = address.city
                    town = address.town
                    viewModel.clearPostalCandidates()
                    message = null
                }
            )
            OutlinedTextField(
                value = prefecture,
                onValueChange = { prefecture = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.facility_prefecture_label)) },
                singleLine = true
            )
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.facility_city_label)) },
                singleLine = true
            )
            OutlinedTextField(
                value = town,
                onValueChange = { town = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.facility_town_label)) },
                singleLine = true
            )
            OutlinedTextField(
                value = streetAddress,
                onValueChange = { streetAddress = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.facility_street_address_label)) },
                minLines = 2
            )
            Button(
                onClick = {
                    val trimmedName = facilityName.trim()
                    val trimmedPostalCode = postalCode.trim()
                    val trimmedPrefecture = prefecture.trim()
                    val trimmedCity = city.trim()
                    val trimmedTown = town.trim()
                    val trimmedStreetAddress = streetAddress.trim()
                    if (trimmedName.isBlank() || trimmedPostalCode.isBlank() ||
                        trimmedPrefecture.isBlank() || trimmedCity.isBlank()
                    ) {
                        message = requiredMessage
                    } else {
                        viewModel.saveFacility(
                            name = trimmedName,
                            postalCode = trimmedPostalCode,
                            prefecture = trimmedPrefecture,
                            city = trimmedCity,
                            town = trimmedTown,
                            streetAddress = trimmedStreetAddress
                        )
                        message = savedMessage
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.facility_save_button))
            }
            message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PostalSearchMessage(
    state: PostalSearchState,
    onAddressSelected: (PostalAddress) -> Unit
) {
    when (state) {
        PostalSearchState.Idle -> Unit
        PostalSearchState.Loading -> Text(stringResource(R.string.facility_postal_searching))
        is PostalSearchState.Error -> Text(
            text = postalSearchErrorText(state.errorCode),
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
        is PostalSearchState.Success -> {
            if (state.results.isEmpty()) {
                Text(stringResource(R.string.facility_postal_not_found))
            } else if (state.results.size == 1) {
                LaunchedEffect(state.results) {
                    onAddressSelected(state.results.first())
                }
                Text(stringResource(R.string.facility_postal_applied))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.facility_postal_candidates),
                        fontWeight = FontWeight.Bold
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.results, key = { "${it.postalCode}${it.prefecture}${it.city}${it.town}" }) { address ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onAddressSelected(address) }
                            ) {
                                Text(
                                    text = "${address.prefecture}${address.city}${address.town}",
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class FacilityRegistrationViewModel(
    private val facilityRepository: FacilityRepository,
    private val postalCodeRepository: PostalCodeRepository = PostalCodeRepository()
) : ViewModel() {
    val facility = facilityRepository.facility

    private val _postalSearchState = MutableStateFlow<PostalSearchState>(PostalSearchState.Idle)
    val postalSearchState: StateFlow<PostalSearchState> = _postalSearchState.asStateFlow()

    fun searchPostalCode(postalCode: String) {
        viewModelScope.launch {
            _postalSearchState.value = PostalSearchState.Loading
            postalCodeRepository.search(postalCode)
                .onSuccess { _postalSearchState.value = PostalSearchState.Success(it) }
                .onFailure {
                    _postalSearchState.value = PostalSearchState.Error(
                        it.message ?: POSTAL_ERROR_SEARCH_FAILED
                    )
                }
        }
    }

    fun clearPostalCandidates() {
        _postalSearchState.value = PostalSearchState.Idle
    }

    fun saveFacility(
        name: String,
        postalCode: String,
        prefecture: String,
        city: String,
        town: String,
        streetAddress: String
    ) {
        facilityRepository.save(
            name = name,
            postalCode = postalCode,
            prefecture = prefecture,
            city = city,
            town = town,
            streetAddress = streetAddress
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as YakuzaiApplication
                FacilityRegistrationViewModel(app.facilityRepository)
            }
        }
    }
}

sealed interface PostalSearchState {
    data object Idle : PostalSearchState
    data object Loading : PostalSearchState
    data class Success(val results: List<PostalAddress>) : PostalSearchState
    data class Error(val errorCode: String) : PostalSearchState
}

@Composable
private fun postalSearchErrorText(errorCode: String): String {
    return when (errorCode) {
        POSTAL_ERROR_INVALID_POSTAL_CODE -> stringResource(R.string.facility_postal_invalid)
        else -> stringResource(R.string.facility_postal_search_failed)
    }
}
