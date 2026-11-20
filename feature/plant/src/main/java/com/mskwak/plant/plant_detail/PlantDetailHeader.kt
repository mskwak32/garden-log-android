package com.mskwak.plant.plant_detail

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mskwak.common_ui.ui_component.TextBadge
import com.mskwak.common_ui.util.clickableWithoutRipple
import com.mskwak.common_ui.util.toDateString
import com.mskwak.plant.R
import java.time.LocalDate

@Composable
private fun PlantImage(
    modifier: Modifier = Modifier,
    imagePath: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(15.dp))
                .then(
                    if (imagePath != null) {
                        Modifier.clickableWithoutRipple(
                            onClickLabel = stringResource(R.string.a11y_view_larger),
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        if (imagePath == null) {
            Box(
                modifier =
                    modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_flower_pot),
                    contentDescription = null,
                    modifier = modifier.size(130.dp),
                )
            }
        } else {
            AsyncImage(
                model = imagePath,
                contentDescription = stringResource(R.string.a11y_plant_photo),
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun Header(
    modifier: Modifier = Modifier,
    imagePath: String?,
    plantName: String,
    isHarvested: Boolean,
    createdAt: LocalDate?,
    harvestDate: LocalDate?,
    onImageClick: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PlantImage(imagePath = imagePath, onClick = onImageClick)

        Spacer(Modifier.height(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plantName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (isHarvested) {
                    Spacer(Modifier.width(8.dp))
                    TextBadge(text = stringResource(R.string.harvested))
                }
            }

            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    text = "${stringResource(R.string.plant_date)} :",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = createdAt.toDateString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (harvestDate != null) {
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        text = "${stringResource(R.string.harvest_date)} :",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = harvestDate.toDateString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
