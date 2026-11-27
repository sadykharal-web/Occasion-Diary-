package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.configureAppCheck
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
import com.example.data.FamilyIdentityMatcher
import com.example.data.FamilyProfile
import com.example.data.LedgerRepository
import com.example.data.UiState
import com.example.data.local.OccasionDiaryDatabase
import com.example.drive.LocalBackupManager
import com.example.reminders.ReminderReceiver
import com.example.ui.components.QuickNeondraSheet
import com.example.ui.screens.ActivityDetailScreen
import com.example.ui.screens.ActivityEditorScreen
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.FamiliesScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.WorldScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemePreferences
import com.example.ui.viewmodel.LedgerViewModel

const val ROUTE_LEDGER = "ledger"
const val ROUTE_FAMILIES = "families"
const val ROUTE_AI = "ai"
const val ROUTE_WORLD = "world"
const val ROUTE_REMINDERS = "reminders"
const val ROUTE_DETAIL = "detail"
const val ROUTE_EDITOR = "editor"
const val ROUTE_CATEGORIES = "categories"

private enum class TopTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    LEDGER(ROUTE_LEDGER, R.string.nav_ledger, Icons.Rounded.Book, Icons.Outlined.Book),
    FAMILIES(ROUTE_FAMILIES, R.string.nav_families, Icons.Rounded.Groups, Icons.Outlined.Groups),
    AI(ROUTE_AI, R.string.nav_ai, Icons.Rounded.Search, Icons.Outlined.Search),
    WORLD(ROUTE_WORLD, R.string.nav_world, Icons.Rounded.Public, Icons.Outlined.Public),
    REMINDERS(ROUTE_REMINDERS, R.string.nav_reminders, Icons.Rounded.Notifications, Icons.Outlined.Notifications)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        ReminderReceiver.ensureNotificationChannel(this)
        setContent {
            val context = LocalContext.current
            val appContext = context.applicationContext
            val repository = remember(appContext) {
                (appContext as? OccasionDiaryApplication)?.repository
                    ?: LedgerRepository(OccasionDiaryDatabase.getInstance(appContext))
            }
            val viewModel: LedgerViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        LedgerViewModel(repository)
                    }
                }
            )
            val systemDark = isSystemInDarkTheme()
            val initialDark = remember(systemDark) {
                ThemePreferences.isDarkMode(context, systemDark)
            }
            LaunchedEffect(systemDark) {
                viewModel.initBackupPrefs(context)
                viewModel.initThemePrefs(context, systemDark)
            }
            val storedDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val activeDarkMode = storedDarkMode ?: initialDark

            MyApplicationTheme(darkTheme = activeDarkMode) {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                Box(modifier = Modifier.fillMaxSize()) {
                    OccasionDiaryAppShell(
                        viewModel = viewModel,
                        isDarkMode = activeDarkMode
                    )
                    AnimatedVisibility(
                        visible = showSplash,
                        exit = fadeOut()
                    ) {
                        SplashScreen(onFinished = { showSplash = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}

@Composable
fun OccasionDiaryAppShell(
    viewModel: LedgerViewModel,
    isDarkMode: Boolean
) {
    val context = LocalContext.current

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedAddress by viewModel.selectedAddress.collectAsStateWithLifecycle()
    val selectedEventTypes by viewModel.selectedEventTypes.collectAsStateWithLifecycle()
    val selectedSortOrder by viewModel.selectedSortOrder.collectAsStateWithLifecycle()
    val dateRangeFilter by viewModel.dateRangeFilter.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val localBackupStatus by viewModel.localBackupStatus.collectAsStateWithLifecycle()
    val aiSearchState by viewModel.aiSearchState.collectAsStateWithLifecycle()
    val selectedSingleFamilyId by viewModel.selectedSingleFamilyId.collectAsStateWithLifecycle()
    val singleFamilyComparisonState by viewModel.singleFamilyComparisonState.collectAsStateWithLifecycle()

    var didAttemptCategorySeed by remember { mutableStateOf(false) }
    LaunchedEffect(dashboardState) {
        val data = (dashboardState as? UiState.Success)?.data
        if (!didAttemptCategorySeed && data != null) {
            didAttemptCategorySeed = true
            viewModel.seedDefaultCategoriesIfEmpty()
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(LocalBackupManager.XLSX_MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            viewModel.exportOfflineBackupToUri(context, uri)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importOfflineBackupFromUri(context, uri)
        }
    }

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
    var prefilledEventName by remember { mutableStateOf<String?>(null) }
    var prefilledEventType by remember { mutableStateOf<String?>(null) }

    var showQuickNeondraSheet by remember { mutableStateOf(false) }
    var prefilledQuickNeondraEventDate by remember { mutableStateOf<Long?>(null) }
    var prefilledQuickNeondraEventName by remember { mutableStateOf<String?>(null) }
    var prefilledQuickNeondraEventType by remember { mutableStateOf<String?>(null) }
    var aiInitialTabIndex by remember { mutableIntStateOf(0) }
    var isWorldFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        if (currentRoute != ROUTE_WORLD) {
            isWorldFullScreen = false
        }
    }

    val allActivities = (dashboardState as? UiState.Success)?.data?.activities ?: emptyList()
    val catalog = (dashboardState as? UiState.Success)?.data?.catalog ?: DropdownCatalog()

    if (showQuickNeondraSheet) {
        QuickNeondraSheet(
            catalog = catalog,
            isSaving = isSaving,
            prefilledEventDateMillis = prefilledQuickNeondraEventDate,
            prefilledEventName = prefilledQuickNeondraEventName,
            prefilledEventType = prefilledQuickNeondraEventType,
            onDismiss = {
                showQuickNeondraSheet = false
                prefilledQuickNeondraEventDate = null
                prefilledQuickNeondraEventName = null
                prefilledQuickNeondraEventType = null
            },
            onSaveNeondra = { name, fatherName, address, eventType, eventDetail, amount, direction ->
                viewModel.saveQuickNeondra(
                    context = context,
                    name = name,
                    fatherName = fatherName,
                    address = address,
                    eventType = eventType,
                    eventDetail = eventDetail,
                    amount = amount,
                    direction = direction,
                    eventDateMillis = prefilledQuickNeondraEventDate ?: System.currentTimeMillis()
                ) {
                    showQuickNeondraSheet = false
                    prefilledQuickNeondraEventDate = null
                    prefilledQuickNeondraEventName = null
                    prefilledQuickNeondraEventType = null
                }
            }
        )
    }

    val showChrome = currentRoute in TopTab.entries.map { it.route } &&
        !(isWorldFullScreen && currentRoute == ROUTE_WORLD)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
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
                                    if (tab == TopTab.AI) {
                                        aiInitialTabIndex = 0
                                    }
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
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
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
                                    if (tab == TopTab.AI) {
                                        aiInitialTabIndex = 0
                                    }
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
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
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
                            selectedEventTypes = selectedEventTypes,
                            onToggleEventTypeFilter = viewModel::toggleEventTypeFilter,
                            onClearEventTypeFilters = viewModel::clearEventTypeFilters,
                            sortOrder = selectedSortOrder,
                            onSortOrderChange = viewModel::setSortOrder,
                            dateRangeFilter = dateRangeFilter,
                            onDateRangeChange = viewModel::setDateRangeFilter,
                            onClearDateRange = viewModel::clearDateRangeFilter,
                            onClearAllFilters = viewModel::clearAllLedgerFilters,
                            onManageCategoriesClick = {
                                navController.navigate(ROUTE_CATEGORIES)
                            },
                            onQuickNeondraClick = {
                                prefilledQuickNeondraEventDate = null
                                prefilledQuickNeondraEventName = null
                                prefilledQuickNeondraEventType = null
                                showQuickNeondraSheet = true
                            },
                            onActivityClick = { act ->
                                selectedActivityId = act.id
                                navController.navigate(ROUTE_DETAIL)
                            },
                            onEditActivityClick = { act ->
                                editingActivity = act
                                prefilledFamily = null
                                prefilledDateMillis = null
                                prefilledEventName = null
                                prefilledEventType = null
                                navController.navigate(ROUTE_EDITOR)
                            },
                            onAddNeondraToEventList = { eventName, eventDateMillis, eventType ->
                                prefilledQuickNeondraEventName = eventName
                                prefilledQuickNeondraEventDate = eventDateMillis
                                prefilledQuickNeondraEventType = eventType
                                showQuickNeondraSheet = true
                            },
                            onAddToEventList = { eventName, eventDateMillis, eventType ->
                                editingActivity = null
                                prefilledFamily = null
                                prefilledDateMillis = eventDateMillis
                                prefilledEventName = eventName
                                prefilledEventType = eventType
                                navController.navigate(ROUTE_EDITOR)
                            },
                            onAddClick = {
                                editingActivity = null
                                prefilledFamily = null
                                prefilledDateMillis = null
                                prefilledEventName = null
                                prefilledEventType = null
                                navController.navigate(ROUTE_EDITOR)
                            }
                        )
                    }

                    composable(ROUTE_FAMILIES) {
                        FamiliesScreen(
                            state = dashboardState,
                            onSaveFamily = { fam -> viewModel.saveFamily(context, fam) },
                            onDeleteFamily = { id -> viewModel.deleteFamily(context, id) },
                            onNewActivityForFamily = { fam ->
                                editingActivity = null
                                prefilledFamily = fam
                                prefilledDateMillis = null
                                prefilledEventName = null
                                prefilledEventType = null
                                navController.navigate(ROUTE_EDITOR)
                            },
                            onActivityClick = { act ->
                                selectedActivityId = act.id
                                navController.navigate(ROUTE_DETAIL)
                            }
                        )
                    }

                    composable(ROUTE_AI) {
                        AiAssistantScreen(
                            dashboardState = dashboardState,
                            aiSearchState = aiSearchState,
                            selectedSingleFamilyId = selectedSingleFamilyId,
                            singleFamilyComparisonState = singleFamilyComparisonState,
                            initialTabIndex = aiInitialTabIndex,
                            onRunAiSearch = { query, _ ->
                                viewModel.runAiSearch(query, useOnlineAi = false)
                            },
                            onClearAiSearch = viewModel::clearAiSearch,
                            onSelectSingleFamily = viewModel::selectSingleFamilyForCompare,
                            onActivityClick = { act ->
                                selectedActivityId = act.id
                                navController.navigate(ROUTE_DETAIL)
                            },
                            onCompareThisFamily = { familyDocId ->
                                viewModel.selectSingleFamilyForCompare(familyDocId)
                                aiInitialTabIndex = 1
                            }
                        )
                    }

                    composable(ROUTE_WORLD) {
                        WorldScreen(
                            isFullScreen = isWorldFullScreen,
                            onToggleFullScreen = { isWorldFullScreen = it }
                        )
                    }

                    composable(ROUTE_REMINDERS) {
                        RemindersScreen(
                            state = dashboardState,
                            backupStatus = localBackupStatus,
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = { enabled ->
                                viewModel.setDarkMode(context, enabled)
                            },
                            onExportBackupClick = {
                                exportBackupLauncher.launch(LocalBackupManager.DEFAULT_BACKUP_FILE_NAME)
                            },
                            onImportBackupClick = {
                                importBackupLauncher.launch(
                                    arrayOf(
                                        LocalBackupManager.XLSX_MIME_TYPE,
                                        "application/vnd.ms-excel",
                                        "application/json",
                                        "*/*"
                                    )
                                )
                            },
                            onManageCategoriesClick = {
                                navController.navigate(ROUTE_CATEGORIES)
                            },
                            onActivityClick = { act ->
                                selectedActivityId = act.id
                                navController.navigate(ROUTE_DETAIL)
                            },
                            onToggleReminder = { act, enabled ->
                                viewModel.toggleReminder(context, act, enabled)
                            }
                        )
                    }

                    composable(ROUTE_CATEGORIES) {
                        CategoriesScreen(
                            state = dashboardState,
                            onBack = { navController.popBackStack() },
                            onSaveCategory = { category, previousName ->
                                viewModel.saveCategory(
                                    context = context,
                                    category = category,
                                    previousName = previousName
                                )
                            },
                            onDeleteCategory = { categoryId ->
                                viewModel.deleteCategory(context, categoryId)
                            },
                            onSeedDefaults = {
                                viewModel.seedDefaultCategoriesIfEmpty()
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
                                    (!selectedRecord.familyDocId.isNullOrBlank() && other.familyDocId == selectedRecord.familyDocId) ||
                                        (FamilyIdentityMatcher.normalizeField(other.name) == FamilyIdentityMatcher.normalizeField(selectedRecord.name) &&
                                            FamilyIdentityMatcher.normalizeField(other.fatherName) == FamilyIdentityMatcher.normalizeField(selectedRecord.fatherName) &&
                                            FamilyIdentityMatcher.normalizeField(other.address) == FamilyIdentityMatcher.normalizeField(selectedRecord.address))
                                }
                            }
                            ActivityDetailScreen(
                                record = selectedRecord,
                                relatedRecords = related,
                                catalog = catalog,
                                onBack = { navController.popBackStack() },
                                onEdit = {
                                    editingActivity = selectedRecord
                                    prefilledFamily = null
                                    prefilledDateMillis = null
                                    prefilledEventName = null
                                    prefilledEventType = null
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
                                    viewModel.markCalendarSynced(context, selectedRecord.id)
                                }
                            )
                        }
                    }

                    composable(ROUTE_EDITOR) {
                        ActivityEditorScreen(
                            initialRecord = editingActivity,
                            prefilledFamily = prefilledFamily,
                            prefilledDateMillis = prefilledDateMillis,
                            prefilledEventName = prefilledEventName,
                            prefilledEventType = prefilledEventType,
                            catalog = catalog,
                            isSaving = isSaving,
                            onBack = { navController.popBackStack() },
                            onQuickCreateCategory = { newCategory ->
                                viewModel.saveCategory(
                                    context = context,
                                    category = newCategory,
                                    previousName = null
                                )
                            },
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
