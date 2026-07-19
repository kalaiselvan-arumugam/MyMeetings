package com.example.mymeetings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object Scanner : NavKey
@Serializable data class Details(val meetingId: Long) : NavKey
@Serializable data class Edit(val meetingId: Long) : NavKey
@Serializable data object Settings : NavKey
