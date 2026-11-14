package com.mskwak.domain.model

import java.time.LocalDate

data class WateringLog(
    val plantId: Int,
    val date: LocalDate
)
