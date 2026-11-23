package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.ActivityCategory
import com.example.data.ActivityRecord
import com.example.data.CategoryStyleCatalog
import com.example.data.UiState
import com.example.ui.components.CategoryBadge
import com.example.ui.viewmodel.LedgerDashboardData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: UiState<LedgerDashboardData>,
    onBack: () -> Unit,
    onSaveCategory: (category: ActivityCategory, previousName: String?) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onSeedDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val data = (state as? UiState.Success)?.data
    val categories = data?.categories ?: emptyList()
    val activities = data?.activities ?: emptyList()

    var showDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<ActivityCategory?>(null) }
    var deletingCategory by remember { mutableStateOf<ActivityCategory?>(null) }

    if (showDialog) {
        CategoryEditorDialog(
            initial = editingCategory,
            onDismiss = { showDialog = false },
            onConfirm = { saved ->
                showDialog = false
                onSaveCategory(saved, editingCategory?.name)
            }
        )
    }

    if (deletingCategory != null) {
        val cat = deletingCategory!!
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Delete Category?") },
            text = { Text("Are you sure you want to delete the \"${cat.name}\" category?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingCategory = null
                        onDeleteCategory(cat.id)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Activity Categories", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("categories_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCategory = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_category_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add New Category")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                            Column {
                                Text(
                                    text = "Custom Categories (${categories.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Assign a distinct color and icon to each occasion. Renaming a category updates all linked entries automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (categories.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "No custom categories yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                OutlinedButton(onClick = onSeedDefaults) {
                                    Text("Load Default Categories (Wedding, Reception, Meeting...)")
                                }
                            }
                        }
                    }
                } else {
                    items(categories, key = { it.id }) { category ->
                        CategoryRowCard(
                            category = category,
                            activities = activities,
                            onEdit = {
                                editingCategory = category
                                showDialog = true
                            },
                            onDelete = { deletingCategory = category }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRowCard(
    category: ActivityCategory,
    activities: List<ActivityRecord>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val usageCount = remember(activities, category.name) {
        activities.count { it.eventType.equals(category.name, ignoreCase = true) }
    }
    val color = category.parsedColor

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("category_card_${category.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = category.parsedIcon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$usageCount entries • ${category.colorHex}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_category_${category.id}")) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit Category")
                }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_category_${category.id}")) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete Category",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorDialog(
    initial: ActivityCategory?,
    onDismiss: () -> Unit,
    onConfirm: (ActivityCategory) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var selectedColorHex by remember {
        mutableStateOf(initial?.colorHex ?: CategoryStyleCatalog.COLOR_PALETTE.first().hex)
    }
    var selectedIconKey by remember {
        mutableStateOf(initial?.iconKey ?: CategoryStyleCatalog.ICON_OPTIONS.first().key)
    }
    var error by remember { mutableStateOf<String?>(null) }

    val previewColor = remember(selectedColorHex) {
        CategoryStyleCatalog.parseColorOrDefault(selectedColorHex)
    }
    val previewIcon = remember(selectedIconKey) {
        CategoryStyleCatalog.iconForKey(selectedIconKey)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Create New Category" else "Edit Category",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Live preview pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Preview:", style = MaterialTheme.typography.labelMedium)
                    CategoryBadge(
                        label = name.ifBlank { "Category Name" },
                        color = previewColor,
                        icon = previewIcon
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name *") },
                    placeholder = { Text("e.g. Wedding, Office, Travel, Education") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_name_input")
                )

                Text("Select Color:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryStyleCatalog.COLOR_PALETTE.forEach { colorOpt ->
                        val color = CategoryStyleCatalog.parseColorOrDefault(colorOpt.hex)
                        val isSelected = selectedColorHex.equals(colorOpt.hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else Modifier
                                )
                                .clickable { selectedColorHex = colorOpt.hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = colorOpt.label,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Text("Select Icon:", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CategoryStyleCatalog.ICON_OPTIONS.forEach { iconOpt ->
                        val isSelected = selectedIconKey == iconOpt.key
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) {
                                previewColor.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            border = if (isSelected) BorderStroke(1.5.dp, previewColor) else null,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { selectedIconKey = iconOpt.key }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = iconOpt.icon,
                                    contentDescription = iconOpt.label,
                                    tint = if (isSelected) previewColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clean = name.trim()
                    if (clean.isEmpty()) {
                        error = "Category name is required."
                        return@TextButton
                    }
                    onConfirm(
                        ActivityCategory(
                            id = initial?.id ?: "",
                            userId = initial?.userId ?: "",
                            name = clean,
                            colorHex = selectedColorHex,
                            iconKey = selectedIconKey
                        )
                    )
                },
                modifier = Modifier.testTag("save_category_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
