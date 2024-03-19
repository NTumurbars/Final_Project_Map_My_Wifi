package com.example.finalproject

object AccessPointTypes {
    val availableAccessPointTypes = listOf(
        AccessPointType(
            name = "Default Access Point",
            range = 40f,
            cost = 25000f,
            imageRes = R.drawable.accesspoint_removebg_preview
        ),
        AccessPointType(
            name = "Test Access Point 2",
            range = 45f,
            cost = 15000f,
            imageRes = R.drawable.accesspoint2_removebg_preview
        ),
        AccessPointType(
            name = "Test Access Point 3",
            range = 50f,
            cost = 20000f,
            imageRes = R.drawable.accesspoint2_removebg_preview
        )

    )
}
