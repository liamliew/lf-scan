package com.lfcreative.lfscan.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Location(
    val id: String,
    val name: String
)
