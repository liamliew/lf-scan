package com.lfcreative.lfscan.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TeamMember(
    val id: String,
    val name: String,
    val pin: String,
    val role: String,
    @SerialName("clerk_user_id") val clerkUserId: String? = null,
    // NULL means no password set — Step 2 of login is skipped for this member (backwards compat).
    val password: String? = null
)
