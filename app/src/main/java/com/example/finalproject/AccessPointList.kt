package com.example.finalproject

object AccessPointTypes {
    val availableAccessPointTypes = listOf(
        AccessPointType(
            name = "Netgear WAC510",
            range = 40f,
            rentalCost = 5000,
            purchaseCost = 25000,
            imageRes = R.drawable.wac510_front_web_main_zoom_tcm171_81899_removebg_preview__1_,
        ),
        AccessPointType(
            name = "Netgear WAX610",
            range = 45f,
            rentalCost = 6000,
            purchaseCost = 30000,
            imageRes = R.drawable.wax610_5_tcm148_151118_removebg_preview__1_
        ),
        AccessPointType(
            name = "Netgear Orbi",
            range = 50f,
            rentalCost = 7000,
            purchaseCost = 35000,
            imageRes = R.drawable.ld0005850760_1__1__removebg_preview__1_
        )
    )
}
