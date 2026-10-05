package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract

@Composable
internal fun MatchingLogicContract.ConditionType.displayName(): String = stringResource(
    when (this) {
        MatchingLogicContract.ConditionType.CONTENT -> R.string.condition_type_content
        MatchingLogicContract.ConditionType.DAY_OF_WEEK -> R.string.condition_type_day_of_week
        MatchingLogicContract.ConditionType.TIME_RANGE -> R.string.condition_type_time_range
    },
)
