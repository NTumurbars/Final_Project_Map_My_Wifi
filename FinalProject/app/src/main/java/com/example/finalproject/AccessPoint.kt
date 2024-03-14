package com.example.finalproject

import java.util.UUID

data class AccessPointType(
    val name: String,
    val range: Float,
    val cost: Float,
    val imageRes: Int
)

data class AccessPointInstance(
    val type: AccessPointType,
    val id: String = UUID.randomUUID().toString(),
    var x: Float = 0f,
    var y: Float = 0f
)