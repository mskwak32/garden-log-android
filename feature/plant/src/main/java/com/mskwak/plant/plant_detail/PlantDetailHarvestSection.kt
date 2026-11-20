package com.mskwak.plant.plant_detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mskwak.common_ui.IconPack
import com.mskwak.common_ui.icon.*
import com.mskwak.common_ui.util.clickableWithoutRipple
import com.mskwak.plant.R

@Composable
internal fun HarvestMemoBox(
    modifier: Modifier = Modifier,
    harvestMemo: String,
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
                ).padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.harvest_memo),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = harvestMemo,
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
internal fun CancelHarvestButton(
    modifier: Modifier = Modifier,
    onEvent: (PlantDetailEvent) -> Unit,
) {
    OutlinedButton(
        onClick = { onEvent(PlantDetailEvent.OnCancelHarvestClicked) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
    ) {
        Text(
            text = stringResource(R.string.cancel_harvest),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
internal fun HarvestSection(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    memoInput: String,
    onEvent: (PlantDetailEvent) -> Unit,
) {
    val harvestStateDescription =
        stringResource(if (isExpanded) R.string.a11y_expanded else R.string.a11y_collapsed)
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
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickableWithoutRipple { onEvent(PlantDetailEvent.OnHarvestSectionToggled) }
                    .semantics { stateDescription = harvestStateDescription },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.harvest),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Image(
                imageVector = IconPack.CaretDown,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(20.dp)
                        .rotate(if (isExpanded) 180f else 0f),
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = memoInput,
                    onValueChange = { onEvent(PlantDetailEvent.OnHarvestMemoChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.harvest_memo_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(10.dp),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onEvent(PlantDetailEvent.OnHarvestClicked) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.harvest),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
