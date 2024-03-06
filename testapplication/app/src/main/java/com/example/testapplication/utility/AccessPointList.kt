package com.example.testapplication.utility

import com.example.testapplication.R
import com.example.testapplication.model.AccessPoint

object AccessPointConfigurations {
    val availableAccessPoints = listOf(
        AccessPoint(
            id = "ap1",
            name = "Test Access Point 1",
            range = 20f,
            cost = 25000f,
            imageRes = R.drawable.accesspoint_removebg_preview
        ),
        AccessPoint(
            id = "ap2",
            name = "Test Access Point 2",
            range = 10f,
            cost = 15000f,
            imageRes = R.drawable.accesspoint2_removebg_preview
        ),
        AccessPoint(
            id = "ap3",
            name = "Test Access Point 3",
            range = 15f,
            cost = 20000f,
            imageRes = R.drawable.accesspoint2_removebg_preview
        )

    )
}
