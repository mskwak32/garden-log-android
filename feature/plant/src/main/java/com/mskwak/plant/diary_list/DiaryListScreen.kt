package com.mskwak.plant.diary_list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.mskwak.common_ui.util.toDateWithDayOfWeek
import com.mskwak.plant.model.DiaryListDayUiModel
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

    var taskDialogState: DiaryListEffect.ShowTaskDialog? by remember {
        mutableStateOf(null)
    }

    Content(
        state = state,
        onEvent = viewModel::setEvent
    )

    taskDialogState?.let { dialogState ->
        DiaryListTaskDialog(
            title = dialogState.title,
            content = dialogState.content,
            onDismiss = { taskDialogState = null }
        )
    }

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

                is DiaryListEffect.ShowTaskDialog -> {
                    taskDialogState = effect
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
    // 빈 화면으로 바뀌었다 돌아와도 유지되도록 목록 밖에서 보관
    val listState = rememberLazyListState()
    val queryKey = listOf(state.currentPlant?.plantId, state.currentYearMonth, state.periodUnit, state.sortOrder)
    var scrolledQueryKey by remember { mutableStateOf(queryKey) }
    // 조회 조건이 바뀐 뒤 새 목록이 도착했을 때만 맨 위로 이동한다.
    // 조건 변경 즉시 이동하면 이전 목록 기준으로 이동한 뒤 key 기반 위치 유지로 다시 어긋날 수 있다.
    LaunchedEffect(state.days) {
        if (scrolledQueryKey != queryKey) {
            listState.scrollToItem(0)
            scrolledQueryKey = queryKey
        }
    }

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
            if (state.days.isEmpty()) {
                ListEmptyView(modifier = Modifier.weight(1f))
            } else {
                DiaryListContent(
                    listState = listState,
                    days = state.days,
                    isYear = state.periodUnit == DiaryListPeriodUnit.YEAR,
                    onEvent = onEvent
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiaryListContent(
    listState: LazyListState,
    days: List<DiaryListDayUiModel>,
    isYear: Boolean,
    onEvent: (DiaryListEvent) -> Unit
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
    ) {
        if (isYear) {
            // 연도별 보기는 목록이 길어 월마다 sticky 헤더로 구분. groupBy가 정렬 순서를 유지한다.
            days.groupBy { it.date.month }.forEach { (month, monthDays) ->
                stickyHeader(key = "month_$month") {
                    MonthHeader(month = month)
                }
                monthDays.forEach { day ->
                    dayGroup(day = day, onEvent = onEvent)
                }
            }
        } else {
            days.forEach { day ->
                dayGroup(day = day, onEvent = onEvent)
            }
        }
    }
}

private fun LazyListScope.dayGroup(
    day: DiaryListDayUiModel,
    onEvent: (DiaryListEvent) -> Unit
) {
    item(key = "date_${day.date}") {
        DateHeader(date = day.date)
    }
    items(items = day.items, key = { it.key }) { item ->
        DiaryListItem(
            item = item,
            onEvent = onEvent,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
    // 그룹 간격은 끝 Spacer로 준다. 헤더에 위 패딩을 주면 월 sticky 헤더와 겹칠 때 여백이 어긋난다.
    item(key = "spacer_${day.date}") {
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    Text(
        text = date.toDateWithDayOfWeek(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 8.dp)
    )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 상단에 붙었을 때 아래 카드가 비치지 않도록 배경을 칠한다
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 2.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = month.getDisplayName(TextStyle.FULL, locale),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
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
            text = stringResource(R.string.diary_list_record_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val previewDays = listOf(
    DiaryListDayUiModel(
        date = LocalDate.of(2023, 11, 12),
        items = listOf(
            DiaryListItemUiModel.Diary(1, emptyList(), "장미에 물을 주었다. 잎이 아주 푸르고 건강해 보인다.", "Rose", false),
            DiaryListItemUiModel.Task(1, "분갈이", "새 화분으로 옮겨 심었다.", "Rose", false),
            DiaryListItemUiModel.Watering(1, LocalDate.of(2023, 11, 12), "Rose", false)
        )
    ),
    DiaryListDayUiModel(
        date = LocalDate.of(2023, 11, 3),
        items = listOf(
            DiaryListItemUiModel.Diary(2, emptyList(), "튤립이 피었다!", "Tulip", true),
            DiaryListItemUiModel.Task(2, "영양제 주기", null, "Tulip", true),
            DiaryListItemUiModel.Watering(2, LocalDate.of(2023, 11, 3), "Tulip", true)
        )
    ),
    DiaryListDayUiModel(
        date = LocalDate.of(2023, 10, 28),
        items = listOf(
            DiaryListItemUiModel.Diary(3, emptyList(), "튤립 구근을 심었다.", "Tulip", false),
            DiaryListItemUiModel.Watering(3, LocalDate.of(2023, 10, 28), "Tulip", false)
        )
    )
)

@Preview(name = "Content - List")
@Composable
private fun ContentPreview() {
    GardenLogTheme {
        val samplePlantFilter = DiaryListPlantFilterUiModel(
            plantId = 1,
            plantName = "Rose"
        )
        val sampleState = DiaryListState(
            currentYearMonth = YearMonth.of(2023, 11),
            currentPlant = samplePlantFilter,
            plantFilterList = listOf(
                samplePlantFilter,
                samplePlantFilter.copy(plantId = 2, plantName = "Tulip")
            ),
            viewOption = DiaryListViewOption(
                periodUnit = DiaryListPeriodUnit.MONTH,
                sortOrder = DiaryListSortOrder.CREATED_LATEST
            ),
            days = previewDays.take(2)
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
        val sampleState = DiaryListState(
            currentYearMonth = YearMonth.of(2023, 11),
            viewOption = DiaryListViewOption(
                periodUnit = DiaryListPeriodUnit.YEAR,
                sortOrder = DiaryListSortOrder.CREATED_LATEST
            ),
            days = previewDays
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
            days = emptyList()
        )

        Content(
            state = sampleState,
            onEvent = {}
        )
    }
}