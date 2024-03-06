package com.example.testapplication.model

data class AccessPoint(
    val id: String,
    val name: String,
    var x: Float = 0f,
    var y: Float = 0f,
    val range: Float,
    val cost: Float,
    val imageRes: Int
)
