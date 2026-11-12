package com.mskwak.plant.diary_list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.mskwak.common_ui.IconPack
import com.mskwak.common_ui.dialog.SelectYearMonthDialog
import com.mskwak.common_ui.icon.ArrowBackIosBlack
import com.mskwak.common_ui.icon.ArrowForwardIosBlack
import com.mskwak.common_ui.theme.GardenLogTheme
import com.mskwak.common_ui.ui_component.LocalNavBottomBarPadding
import com.mskwak.domain.model.DiaryListPeriodUnit
import com.mskwak.domain.model.DiaryListSortOrder
import com.mskwak.domain.model.DiaryListViewOption
import com.mskwak.plant.R
import com.mskwak.plant.model.DiaryListItemUiModel
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle

@Composable
fun DiaryListScreen(
    viewModel: DiaryListViewModel = hiltViewModel(),
    navigate: (DiaryListEffect.Navigation) -> Unit
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    var selectYearMonthDialogState: DiaryListEffect.ShowSelectYearMonth? by remember {
        mutableStateOf(null)
    }

    Content(
        state = state,
        onEvent = viewModel::setEvent
    )

    selectYearMonthDialogState?.let { dialogState ->
        SelectYearMonthDialog(
            initialYearMonth = dialogState.yearMonth,
            showMonth = dialogState.showMonth,
            onDismiss = { selectYearMonthDialogState = null },
            onConfirm = { selectedYearMonth ->
                viewModel.setEvent(DiaryListEvent.YearMonthChanged(selectedYearMonth))
                selectYearMonthDialogState = null
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is DiaryListEffect.ShowSelectYearMonth -> {
                    selectYearMonthDialogState = effect
                }

                is DiaryListEffect.Navigation -> {
                    navigate(effect)
                }
            }
        }
    }
}

@Composable
private fun Content(
    state: DiaryListState,
    onEvent: (DiaryListEvent) -> Unit
) {
    Scaffold(
        topBar = {
            TopBar(
                onEvent = onEvent,
                yearMonth = state.currentYearMonth,
                periodUnit = state.periodUnit
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            .union(WindowInsets(bottom = LocalNavBottomBarPadding.current))
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DiaryListPeriodUnitToggle(
                    currentPeriodUnit = state.periodUnit,
                    onEvent = onEvent
                )
                DiaryListSortFilter(
                    currentSortOrder = state.sortOrder,
                    onEvent = onEvent
                )
            }

            Spacer(Modifier.height(16.dp))
            DiaryListPlantFilter(
                currentFilter = state.currentPlant,
                plantList = state.plantFilterList,
                onEvent = onEvent
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (state.diaries.isEmpty()) {
                ListEmptyView(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.periodUnit == DiaryListPeriodUnit.YEAR) {
                        // 연도별 보기는 목록이 길어 월마다 헤더로 구분. groupBy가 정렬 순서를 유지한다.
                        state.diaries.groupBy { it.date.month }.forEach { (month, diaries) ->
                            item(key = "month_header_$month") {
                                MonthHeader(month = month)
                            }
                            items(
                                items = diaries,
                                key = { it.id }
                            ) { item ->
                                DiaryListItem(
                                    diary = item,
                                    onEvent = onEvent
                                )
                            }
                        }
                    } else {
                        items(
                            items = state.diaries,
                            key = { it.id }
                        ) { item ->
                            DiaryListItem(
                                diary = item,
                                onEvent = onEvent
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onEvent: (DiaryListEvent) -> Unit,
    yearMonth: YearMonth,
    periodUnit: DiaryListPeriodUnit
) {
    val isYear = periodUnit == DiaryListPeriodUnit.YEAR

    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PeriodArrowButton(
                    imageVector = IconPack.ArrowBackIosBlack,
                    contentDescription = stringResource(
                        if (isYear) R.string.previous_year else R.string.previous_month
                    ),
                    onClick = { onEvent(DiaryListEvent.OnPreviousPeriodClicked) }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isYear) {
                        stringResource(R.string.year_format, yearMonth.year)
                    } else {
                        yearMonth.toString()
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = { onEvent(DiaryListEvent.SelectPeriod) })
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
                Spacer(Modifier.width(8.dp))
                PeriodArrowButton(
                    imageVector = IconPack.ArrowForwardIosBlack,
                    contentDescription = stringResource(
                        if (isYear) R.string.next_year else R.string.next_month
                    ),
                    onClick = { onEvent(DiaryListEvent.OnNextPeriodClicked) }
                )
            }
        },
        actions = {
            Text(
                text = stringResource(if (isYear) R.string.this_year else R.string.this_month),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        onClick = { onEvent(DiaryListEvent.OnCurrentPeriodClicked) }
                    )
                    .padding(10.dp)
            )
            // actions 슬롯 기본 여백 4dp와 합쳐 화면 끝에서 16dp
            Spacer(Modifier.width(12.dp))
        }
    )
}

/**
 * 클릭 영역은 30dp로 유지하고 아이콘만 작게 그린다.
 */
@Composable
private fun PeriodArrowButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MonthHeader(month: Month) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text = month.getDisplayName(TextStyle.FULL, locale),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}

@Composable
private fun ListEmptyView(
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isPreview) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
            )
        } else {
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_notebook))
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(120.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.diary_list_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "Content - List")
@Composable
private fun ContentPreview() {
    val now = LocalDate.now()
    GardenLogTheme {
        val samplePlantFilter = DiaryListPlantFilterUiModel(
            plantId = 1,
            plantName = "Rose"
        )
        val sampleDiary = DiaryListItemUiModel(
            id = 1,
            date = now,
            content = "Watered the rose plant and it seems to be doing well. The leaves are very green and healthy.",
            imagePath = null,
            plantName = "Rose"
        )
        val sampleState = DiaryListState(
            currentYearMonth = YearMonth.of(2023, 10),
            currentPlant = samplePlantFilter,
            plantFilterList = listOf(
                samplePlantFilter,
                samplePlantFilter.copy(plantId = 2, plantName = "Tulip")
            ),
            viewOption = DiaryListViewOption(
                periodUnit = DiaryListPeriodUnit.MONTH,
                sortOrder = DiaryListSortOrder.CREATED_LATEST
            ),
            diaries = listOf(
                sampleDiary,
                sampleDiary.copy(
                    id = 2,
                    date = now.minusDays(2),
                    plantName = "Tulip",
                    content = "Tulip is blooming!"
                ),
                sampleDiary.copy(
                    id = 3,
                    date = now.minusDays(3),
                    plantName = "Rose",
                    content = "Found a pest on the rose, applied treatment."
                )
            )
        )

        Content(
            state = sampleState,
            onEvent = {}
        )
    }
}

@Preview(name = "Content - Year")
@Composable
private fun ContentYearPreview() {
    GardenLogTheme {
        val sampleDiary = DiaryListItemUiModel(
            id = 1,
            date = LocalDate.of(2023, 11, 12),
            content = "Watered the rose plant.",
            imagePath = null,
            plantName = "Rose"
        )
        val sampleState = DiaryListState(
            currentYearMonth = YearMonth.of(2023, 11),
            viewOption = DiaryListViewOption(
                periodUnit = DiaryListPeriodUnit.YEAR,
                sortOrder = DiaryListSortOrder.CREATED_LATEST
            ),
            diaries = listOf(
                sampleDiary,
                sampleDiary.copy(id = 2, date = LocalDate.of(2023, 10, 28), content = "Planted tulip bulbs."),
                sampleDiary.copy(id = 3, date = LocalDate.of(2023, 10, 5), content = "Repotted the rose.")
            )
        )

        Content(
            state = sampleState,
            onEvent = {}
        )
    }
}

@Preview(name = "Content - Empty")
@Composable
private fun ContentEmptyPreview() {
    GardenLogTheme {
        val sampleState = DiaryListState(
            currentYearMonth = YearMonth.of(2023, 10),
            diaries = emptyList()
        )

        Content(
            state = sampleState,
            onEvent = {}
        )
    }
}