package com.mskwak.plant.diary_list

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mskwak.common_ui.theme.PreviewTheme
import com.mskwak.domain.model.DiaryListPeriodUnit
import com.mskwak.plant.R

@Composable
fun DiaryListPeriodUnitToggle(
    modifier: Modifier = Modifier,
    currentPeriodUnit: DiaryListPeriodUnit,
    onEvent: (DiaryListEvent) -> Unit
) {
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(3.dp)
            .selectableGroup()
    ) {
        DiaryListPeriodUnit.entries.forEach { unit ->
            Segment(
                text = when (unit) {
                    DiaryListPeriodUnit.MONTH -> stringResource(R.string.period_unit_month)
                    DiaryListPeriodUnit.YEAR -> stringResource(R.string.period_unit_year)
                },
                isSelected = unit == currentPeriodUnit,
                onClick = {
                    if (unit != currentPeriodUnit) {
                        onEvent(DiaryListEvent.PeriodUnitChanged(unit))
                    }
                }
            )
        }
    }
}

@Composable
private fun Segment(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val shape = RoundedCornerShape(6.dp)

    // 테두리 안쪽 여백(3dp)과 합쳐 정렬 버튼(세로 패딩 8dp)과 높이를 맞춘다.
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (isSelected) colorScheme.secondaryContainer else Color.Transparent)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true, name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiaryListPeriodUnitTogglePreview() {
    PreviewTheme {
        DiaryListPeriodUnitToggle(
            currentPeriodUnit = DiaryListPeriodUnit.MONTH,
            onEvent = {}
        )
    }
}
