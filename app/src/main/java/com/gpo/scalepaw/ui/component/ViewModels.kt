package com.gpo.scalepaw.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpo.scalepaw.AppContainer
import com.gpo.scalepaw.container

/** A ViewModel scoped to the current nav entry, built from the app container. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.container
    return viewModel(key = key) { create(container) }
}
