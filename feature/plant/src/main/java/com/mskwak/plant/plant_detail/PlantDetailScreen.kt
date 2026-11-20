package com.mskwak.plant.plant_detail

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mskwak.common_ui.IconPack
import com.mskwak.common_ui.icon.*
import com.mskwak.common_ui.theme.GardenLogTheme
import com.mskwak.common_ui.ui_component.AppDropDownMenu
import com.mskwak.plant.R
import com.mskwak.plant.dialog.ExactAlarmPermissionDialog
import com.mskwak.plant.dialog.ImageZoomDialog
import com.mskwak.plant.model.WateringStatus
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun PlantDetailScreen(
    viewModel: PlantDetailViewModel = hiltViewModel(),
    navigate: (PlantDetailEffect.Navigation) -> Unit,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (state.calendarTarget != null) PlantDetailRecordCalendarDialog(state, viewModel::setEvent)
    state.taskDialog?.let {
        PlantDetailTaskDialog(state = it, isSaving = state.isTaskSaving, onEvent = viewModel::setEvent)
    }
    var showExactAlarmPermissionDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHarvestDialog by remember { mutableStateOf(false) }
    var taskToComplete by remember { mutableStateOf<PlantDetailEffect.ShowOneTimeTaskCompleteDialog?>(null) }
    var taskIdToDelete by remember { mutableStateOf<Int?>(null) }

    taskToComplete?.let { target ->
        PlantDetailTaskCompleteDialog(
            onConfirm = {
                taskToComplete = null
                viewModel.setEvent(
                    PlantDetailEvent.OnOneTimeTaskCompleteConfirmed(target.taskId, target.date),
                )
            },
            onDismiss = { taskToComplete = null },
        )
    }

    taskIdToDelete?.let { taskId ->
        PlantDetailTaskDeleteDialog(
            onConfirm = {
                taskIdToDelete = null
                viewModel.setEvent(PlantDetailEvent.OnTaskDeleteConfirmed(taskId))
            },
            onDismiss = { taskIdToDelete = null },
        )
    }

    if (showExactAlarmPermissionDialog) {
        ExactAlarmPermissionDialog(
            onDismiss = { showExactAlarmPermissionDialog = false },
        )
    }

    if (showDeleteDialog) {
        PlantDetailDeleteDialog(
            onConfirm = {
                viewModel.setEvent(PlantDetailEvent.OnDeleteConfirmClicked)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    if (showHarvestDialog) {
        PlantDetailHarvestDialog(
            onConfirm = { date ->
                showHarvestDialog = false
                viewModel.setEvent(PlantDetailEvent.OnHarvestConfirmed(date))
            },
            onDismiss = { showHarvestDialog = false },
        )
    }

    Content(
        state = state,
        onEvent = viewModel::setEvent,
    )

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PlantDetailEffect.Navigation -> {
                    navigate(effect)
                }

                is PlantDetailEffect.ShowSnackbar -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                }

                is PlantDetailEffect.ShowExactAlarmPermissionDialog -> {
                    showExactAlarmPermissionDialog = true
                }

                is PlantDetailEffect.ShowDeleteConfirmDialog -> {
                    showDeleteDialog = true
                }

                is PlantDetailEffect.ShowHarvestConfirmDialog -> {
                    showHarvestDialog = true
                }

                is PlantDetailEffect.ShowOneTimeTaskCompleteDialog -> {
                    taskToComplete = effect
                }

                is PlantDetailEffect.ShowTaskDeleteConfirmDialog -> {
                    taskIdToDelete = effect.taskId
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onEvent: (PlantDetailEvent) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(
                onClick = { onEvent(PlantDetailEvent.OnBackClicked) },
            ) {
                Icon(
                    imageVector = IconPack.ArrowBackBlack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = IconPack.MoreHorizBlack,
                        contentDescription = stringResource(R.string.menu),
                        modifier = Modifier.rotate(90f),
                    )
                }
                AppDropDownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = {
                            showMenu = false
                            onEvent(PlantDetailEvent.OnEditPlantClicked)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export)) },
                        onClick = {
                            showMenu = false
                            onEvent(PlantDetailEvent.OnExportClicked)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            showMenu = false
                            onEvent(PlantDetailEvent.OnDeletePlantClicked)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun Content(
    state: PlantDetailState,
    onEvent: (PlantDetailEvent) -> Unit,
) {
    var wateringAnimationKey by remember { mutableIntStateOf(0) }
    var zoomImagePath by remember { mutableStateOf<String?>(null) }

    zoomImagePath?.let {
        ImageZoomDialog(imagePath = it, onDismiss = { zoomImagePath = null })
    }

    Scaffold(
        topBar = { TopBar(onEvent = onEvent) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 30.dp),
        ) {
            Header(
                imagePath = state.plantImagePath,
                plantName = state.plantName,
                isHarvested = state.isHarvested,
                createdAt = state.createdAt,
                harvestDate = state.harvestDate,
                onImageClick = { state.plantImagePath?.let { zoomImagePath = it } },
            )

            // 수확 전에만 물주기 정보 표시
            if (!state.isHarvested) {
                Spacer(Modifier.height(16.dp))
                WateringInfo(
                    dDays = state.dDays,
                    wateringStatus = state.wateringStatus,
                    lastWateringDate = state.lastWateringDate,
                    wateringAlarm = state.wateringAlarmTime,
                    isWateringActive = state.isWateringActive,
                    wateringAnimationKey = wateringAnimationKey,
                    onEvent = onEvent,
                )
            }

            Spacer(Modifier.height(16.dp))
            WateringSection(
                memo = state.memo,
                isHarvested = state.isHarvested,
                wateringStatus = state.wateringStatus,
                onEvent = { event ->
                    if (event is PlantDetailEvent.OnWateringClicked) {
                        wateringAnimationKey++
                    }
                    if (event is PlantDetailEvent.OnCancelWateringClicked) {
                        wateringAnimationKey = 0
                    }
                    onEvent(event)
                },
            )

            // 수확 전에만 할일 카드와 추가 버튼 표시
            if (!state.isHarvested) {
                state.tasks.forEach { task ->
                    // 중간 카드가 사라져도 카드별 취소 버튼 애니메이션 상태가 섞이지 않게 한다.
                    key(task.id) {
                        Spacer(Modifier.height(16.dp))
                        PlantDetailTaskCard(task = task, onEvent = onEvent)
                    }
                }
                Spacer(Modifier.height(16.dp))
                PlantDetailAddTaskButton(onClick = { onEvent(PlantDetailEvent.OnAddTaskClicked) })
            }

            // 수확된 경우 수확 메모 표시
            if (state.isHarvested && state.harvestMemo != null) {
                Spacer(Modifier.height(16.dp))
                HarvestMemoBox(harvestMemo = state.harvestMemo)
            }

            Spacer(Modifier.height(16.dp))
            DiarySection(
                plantName = state.plantName,
                diaryList = state.diaries,
                onEvent = onEvent,
            )

            Spacer(Modifier.height(16.dp))
            if (state.isHarvested) {
                CancelHarvestButton(onEvent = onEvent)
            } else {
                HarvestSection(
                    isExpanded = state.isHarvestSectionExpanded,
                    memoInput = state.harvestMemoInput,
                    onEvent = onEvent,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewContent() {
    val state =
        PlantDetailState(
            plantImagePath = null,
            plantName = "바질",
            createdAt = LocalDate.now(),
            dDays = 1,
            wateringStatus = WateringStatus.TODAY_DONE,
            lastWateringDate = LocalDate.now().minusDays(3),
            wateringAlarmTime = LocalTime.of(9, 0),
            isWateringActive = true,
            memo = "물을 줄 때는 흙이 마른 것을 확인하고 화분 받침대에 고인 물은 버려주세요.",
        )
    GardenLogTheme {
        Content(
            state = state,
            onEvent = {},
        )
    }
}
