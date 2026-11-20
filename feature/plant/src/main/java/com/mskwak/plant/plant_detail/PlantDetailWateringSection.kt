package com.mskwak.plant.plant_detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.mskwak.common_ui.IconPack
import com.mskwak.common_ui.icon.*
import com.mskwak.common_ui.ui_component.Switch
import com.mskwak.common_ui.util.clickableWithoutRipple
import com.mskwak.common_ui.util.toDateString
import com.mskwak.common_ui.util.toTimeString
import com.mskwak.domain.Constants
import com.mskwak.plant.R
import com.mskwak.plant.model.WateringStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Composable
internal fun WateringInfo(
    modifier: Modifier = Modifier,
    dDays: Int,
    wateringStatus: WateringStatus,
    lastWateringDate: LocalDate?,
    wateringAlarm: LocalTime?,
    isWateringActive: Boolean,
    wateringAnimationKey: Int,
    onEvent: (PlantDetailEvent) -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(15.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(15.dp),
                ).padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 16.dp),
    ) {
        val lottieComposition by rememberLottieComposition(
            LottieCompositionSpec.RawRes(R.raw.lottie_water_loader),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(35.dp),
            ) {
                if (wateringAnimationKey > 0) {
                    key(wateringAnimationKey) {
                        LottieAnimation(
                            composition = lottieComposition,
                            iterations = 1,
                            modifier =
                                Modifier
                                    .size(35.dp)
                                    .scale(1.8f),
                        )
                        Image(
                            imageVector = IconPack.WaterDropWhite,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                } else {
                    Image(
                        imageVector =
                            when (wateringStatus) {
                                WateringStatus.TODAY,
                                WateringStatus.UPCOMING,
                                WateringStatus.NO_PERIOD,
                                -> IconPack.WaterDropBlue

                                WateringStatus.TODAY_DONE -> IconPack.WaterDropWithBackground

                                WateringStatus.OVERDUE -> IconPack.WaterDropRed
                            },
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Spacer(Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.watering),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text =
                            when (wateringStatus) {
                                WateringStatus.NO_PERIOD,
                                WateringStatus.OVERDUE,
                                -> {
                                    stringResource(R.string.watering_d_day_plus_format, dDays)
                                }

                                else -> {
                                    stringResource(R.string.watering_d_day_minus_format, dDays)
                                }
                            },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                run {
                    val lastWateringText =
                        if (lastWateringDate == null) {
                            stringResource(R.string.watering_no_record)
                        } else {
                            run {
                                val today = LocalDate.now()
                                when (val daysBetween = ChronoUnit.DAYS.between(lastWateringDate, today)) {
                                    0L -> {
                                        stringResource(R.string.today)
                                    }

                                    in 1..Constants.LAST_WATERING_TEXT_CRITERION -> {
                                        stringResource(R.string.days_ago_format, daysBetween)
                                    }

                                    else -> {
                                        lastWateringDate.toDateString()
                                    }
                                }
                            }
                        }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${stringResource(R.string.watering_last_date)}: $lastWateringText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (wateringAlarm != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text =
                        stringResource(R.string.watering_alarm) +
                            ": ${wateringAlarm.toTimeString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Switch(
                    checked = isWateringActive,
                    onCheckedChange = { onEvent(PlantDetailEvent.ToggleWateringAlarmActive(it)) },
                    width = 36.dp,
                )
            }
        }
    }
}

@Composable
internal fun WateringSection(
    modifier: Modifier = Modifier,
    memo: String?,
    isHarvested: Boolean,
    wateringStatus: WateringStatus,
    onEvent: (PlantDetailEvent) -> Unit,
) {
    // 최초 진입 완료 상태는 즉시 보이고, 이후 상태 변경은 기존 전환을 유지한다.
    val cancelButtonState =
        remember {
            MutableTransitionState(wateringStatus == WateringStatus.TODAY_DONE)
        }

    // DB 복원과 취소 모두 상태 전환 애니메이션으로 반영한다.
    LaunchedEffect(wateringStatus) {
        cancelButtonState.targetState = wateringStatus == WateringStatus.TODAY_DONE
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(15.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(15.dp),
                ).padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        .clickable { onEvent(PlantDetailEvent.OnWateringCalendarClicked) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Calendar, contentDescription = stringResource(R.string.watering_calendar), tint = MaterialTheme.colorScheme.onPrimary)
            }
            if (!isHarvested) {
                Spacer(Modifier.width(8.dp))
                WateringButton(
                    modifier = Modifier.weight(1f),
                    onEvent = onEvent,
                )
                AnimatedVisibility(
                    visibleState = cancelButtonState,
                    enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
                ) {
                    // padding(start)을 AnimatedVisibility 안으로 넣어야 gap도 함께 shrink됨
                    WateringCancelButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onEvent = onEvent,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Text(
            text = memo ?: "${stringResource(R.string.memo)}:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(10.dp),
                    ).padding(10.dp),
        )
    }
}

@Composable
private fun WateringButton(
    modifier: Modifier = Modifier,
    onEvent: (PlantDetailEvent) -> Unit,
) {
    Row(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                .clickableWithoutRipple { onEvent(PlantDetailEvent.OnWateringClicked) }
                .heightIn(min = 48.dp)
                .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            imageVector = IconPack.WaterDropWhite,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onTertiary),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.watering),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
private fun WateringCancelButton(
    modifier: Modifier = Modifier,
    onEvent: (PlantDetailEvent) -> Unit,
) {
    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(15.dp))
                .clickableWithoutRipple { onEvent(PlantDetailEvent.OnCancelWateringClicked) }
                .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.cancel),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
