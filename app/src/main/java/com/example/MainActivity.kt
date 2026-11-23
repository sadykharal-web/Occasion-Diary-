package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
import com.example.data.FamilyProfile
import com.example.data.LedgerRepository
import com.example.data.UiState
import com.example.reminders.ReminderReceiver
import com.example.ui.screens.ActivityDetailScreen
import com.example.ui.screens.ActivityEditorScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.FamiliesScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LedgerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_LEDGER = "ledger"
const val ROUTE_CALENDAR = "calendar"
const val ROUTE_FAMILIES = "families"
const val ROUTE_REMINDERS = "reminders"
const val ROUTE_DETAIL = "detail"
const val ROUTE_EDITOR = "editor"

private enum class TopTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    LEDGER(ROUTE_LEDGER, R.string.nav_ledger, Icons.Rounded.Book, Icons.Outlined.Book),
    CALENDAR(ROUTE_CALENDAR, R.string.nav_calendar, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    FAMILIES(ROUTE_FAMILIES, R.string.nav_families, Icons.Rounded.Groups, Icons.Outlined.Groups),
    REMINDERS(ROUTE_REMINDERS, R.string.nav_reminders, Icons.Rounded.Notifications, Icons.Outlined.Notifications)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderReceiver.ensureNotificationChannel(this)
        setContent {
            MyApplicationTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            },
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
        )
    } else {
        key(user.uid) {
            AuthenticatedAppShell(
                currentUserId = user.uid,
                userEmail = user.email,
                userDisplayName = user.displayName
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedAppShell(
    currentUserId: String,
    userEmail: String?,
    userDisplayName: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: LedgerViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                LedgerViewModel(LedgerRepository(db), currentUserId)
            }
        }
    )

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedAddress by viewModel.selectedAddress.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        val msg = actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearActionMessage()
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var selectedActivityId by remember { mutableStateOf<String?>(null) }
    var editingActivity by remember { mutableStateOf<ActivityRecord?>(null) }
    var prefilledFamily by remember { mutableStateOf<FamilyProfile?>(null) }
    var prefilledDateMillis by remember { mutableStateOf<Long?>(null) }

    val allActivities = (dashboardState as? UiState.Success)?.data?.activities ?: emptyList()
    val catalog = (dashboardState as? UiState.Success)?.data?.catalog ?: DropdownCatalog()

    val showChrome = currentRoute in TopTab.entries.map { it.route }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (showChrome) {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            bottomBar = {
                if (showChrome && !useNavigationRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        TopTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChrome && useNavigationRail) {
                    NavigationRail {
                        TopTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_LEDGER,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_LEDGER) {
                        LedgerScreen(
                            state = dashboardState,
                            searchQuery = searchQuery,
                            onSearchQueryChange = viewModel::setSearchQuery,
                            selectedFilter = selectedFilter,
                            onFilterChange = viewModel::setFilter,
                            selectedAddress = selectedAddress,
                            onAddressFilterChange = viewModel::setAddressFilter,
                            onActivityClick = { act ->
                                selectedActivityId = act.id
                                navController.navigate(ROUTE_DETAIL)
                            },
                            onAddClick = {
                                editingActivity = null
                                prefilledFamily = null
                                prefilledDateMillis = null
                                navController.navigate(ROUTE_EDITOR)
                            }
                        )
                    }

                    composable(ROUTE_CALENDAR) {
                        CalendarScreen(
                            state = dashboardState,
                            onActivityClick = { act ->
                                selectedActivityId = act.id
                                navController.navigate(ROUTE_DETAIL)
                            },
                            onAddOnDate = { dateMillis ->
                                editingActivity = null
                                prefilledFamily = null
                                prefilledDateMillis = dateMillis
                                navController.navigate(ROUTE_EDITOR)
                            },
                            onCalendarSynced = { actId ->
                                viewModel.markCalendarSynced(actId)
                            }
                        )
                    }

                    composable(ROUTE_FAMILIES) {
                        FamiliesScreen(
                            state = dashboardState,
                            onSaveFamily = { fam -> viewModel.saveFamily(fam) },
                            onDeleteFamily = { id -> viewModel.deleteFamily(id) },
                            onNewActivityForFamily = { fam ->
                                editingActivity = null
                                prefilledFamily = fam
                                prefilledDateMillis = null
                                navController.navigate(ROUTE_EDITOR)
                            }
                        )
                    }

                    composable(ROUTE_REMINDERS) {
                        RemindersScreen(
                            state = dashboardState,
                            userEmail = userEmail,
                            userDisplayName = userDisplayName,
                            onActivityClick = { act ->
                                selectedActivityId = act.id
                                navController.navigate(ROUTE_DETAIL)
                            },
                            onToggleReminder = { act, enabled ->
                                viewModel.toggleReminder(context, act, enabled)
                            },
                            onSignOutClick = {
                                signOut(credentialManager = credentialManager, scope = scope)
                            }
                        )
                    }

                    composable(ROUTE_DETAIL) {
                        val selectedRecord = allActivities.firstOrNull { it.id == selectedActivityId }
                        if (selectedRecord == null) {
                            LaunchedEffect(Unit) {
                                navController.popBackStack()
                            }
                        } else {
                            val related = remember(allActivities, selectedRecord) {
                                allActivities.filter { other ->
                                    (selectedRecord.familyDocId != null && other.familyDocId == selectedRecord.familyDocId) ||
                                        (selectedRecord.familyId.isNotBlank() &&
                                            other.familyId == selectedRecord.familyId &&
                                            other.name.equals(selectedRecord.name, ignoreCase = true))
                                }
                            }
                            ActivityDetailScreen(
                                record = selectedRecord,
                                relatedRecords = related,
                                onBack = { navController.popBackStack() },
                                onEdit = {
                                    editingActivity = selectedRecord
                                    prefilledFamily = null
                                    prefilledDateMillis = null
                                    navController.navigate(ROUTE_EDITOR)
                                },
                                onDelete = {
                                    viewModel.deleteActivity(context, selectedRecord.id) {
                                        navController.popBackStack()
                                    }
                                },
                                onToggleReminder = { enabled ->
                                    viewModel.toggleReminder(context, selectedRecord, enabled)
                                },
                                onCalendarSynced = {
                                    viewModel.markCalendarSynced(selectedRecord.id)
                                }
                            )
                        }
                    }

                    composable(ROUTE_EDITOR) {
                        ActivityEditorScreen(
                            initialRecord = editingActivity,
                            prefilledFamily = prefilledFamily,
                            prefilledDateMillis = prefilledDateMillis,
                            catalog = catalog,
                            isSaving = isSaving,
                            onBack = { navController.popBackStack() },
                            onSave = { record, _ ->
                                viewModel.saveActivity(context, record) { saved ->
                                    selectedActivityId = saved.id
                                    navController.popBackStack()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
