package com.example.anan.navigation

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object MyProfile : Screen("my_profile")
    object EditProfile : Screen("edit_profile")
    object Translation : Screen("translation")
    object FolderDetail : Screen("folder_detail/{folderId}") {
        fun createRoute(folderId: String) = "folder_detail/$folderId"
    }
    object UserAgreement : Screen("user_agreement")
}
