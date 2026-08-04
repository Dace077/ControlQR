package com.controlqr.acceso.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.controlqr.acceso.LocalAppContainer
import com.controlqr.acceso.data.db.ScanType
import com.controlqr.acceso.ui.auth.ChangePasswordScreen
import com.controlqr.acceso.ui.auth.LoginScreen
import com.controlqr.acceso.ui.auth.ProvisionScreen
import com.controlqr.acceso.ui.auth.SetupScreen
import com.controlqr.acceso.ui.guard.GuardHomeScreen
import com.controlqr.acceso.ui.guard.ScanScreen
import com.controlqr.acceso.ui.master.EventsScreen
import com.controlqr.acceso.ui.master.IssuePassScreen
import com.controlqr.acceso.ui.master.MasterHomeScreen
import com.controlqr.acceso.ui.master.PassesScreen
import com.controlqr.acceso.ui.master.ReportsScreen
import com.controlqr.acceso.ui.master.SettingsScreen
import com.controlqr.acceso.ui.master.UsersScreen
import com.controlqr.acceso.ui.vm.AdminViewModel
import com.controlqr.acceso.ui.vm.IssueViewModel
import com.controlqr.acceso.ui.vm.ScanViewModel
import com.controlqr.acceso.ui.vm.SessionViewModel

private object Routes {
    const val MASTER_HOME = "master_home"
    const val GUARD_HOME = "guard_home"
    const val ISSUE = "issue"
    const val PASSES = "passes"
    const val REPORTS = "reports"
    const val USERS = "users"
    const val EVENTS = "events"
    const val SETTINGS = "settings"
    const val CHANGE_PASSWORD = "change_password"
    const val SCAN_ENTRY = "scan_entry"
    const val SCAN_EXIT = "scan_exit"
}

/**
 * Punto de entrada de la interfaz.
 *
 * El grafo de navegación se construye según el rol: el vasallo ni siquiera tiene
 * registradas las rutas del master, así que no hay forma de llegar a ellas.
 */
@Composable
fun ControlQrRoot() {
    val container = LocalAppContainer.current
    val session: SessionViewModel = viewModel(factory = SessionViewModel.factory(container))
    val state by session.state.collectAsStateWithLifecycle()

    var provisioning by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            provisioning -> ProvisionScreen(
                state = state,
                viewModel = session,
                onBack = { provisioning = false }
            )

            state.needsSetup -> SetupScreen(
                state = state,
                viewModel = session,
                onProvisionRequested = {
                    session.clearFeedback()
                    provisioning = true
                }
            )

            !state.signedIn -> LoginScreen(
                state = state,
                viewModel = session,
                onProvisionRequested = {
                    session.clearFeedback()
                    provisioning = true
                }
            )

            state.mustChangePassword -> ChangePasswordScreen(
                state = state,
                viewModel = session,
                forced = true,
                onDone = { session.refresh() }
            )

            else -> MainGraph(session, state)
        }
    }

    // Al terminar de vincular, el flujo vuelve solo al login del sitio recién configurado.
    LaunchedEffect(state.needsSetup, state.busy, state.error) {
        if (provisioning && !state.needsSetup && !state.busy && state.error == null) {
            provisioning = false
        }
    }
}

@Composable
private fun MainGraph(session: SessionViewModel, state: SessionViewModel.State) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val user = state.user ?: return

    val admin: AdminViewModel = viewModel(factory = AdminViewModel.factory(container))

    NavHost(
        navController = navController,
        startDestination = if (state.isMaster) Routes.MASTER_HOME else Routes.GUARD_HOME
    ) {
        if (state.isMaster) {
            composable(Routes.MASTER_HOME) {
                MasterHomeScreen(
                    displayName = user.displayName,
                    siteName = state.siteName,
                    viewModel = admin,
                    onIssue = { navController.navigate(Routes.ISSUE) },
                    onScanEntry = { navController.navigate(Routes.SCAN_ENTRY) },
                    onScanExit = { navController.navigate(Routes.SCAN_EXIT) },
                    onPasses = { navController.navigate(Routes.PASSES) },
                    onReports = { navController.navigate(Routes.REPORTS) },
                    onUsers = { navController.navigate(Routes.USERS) },
                    onEvents = { navController.navigate(Routes.EVENTS) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onSignOut = session::signOut
                )
            }

            composable(Routes.ISSUE) {
                val issueVm: IssueViewModel = viewModel(factory = IssueViewModel.factory(container))
                IssuePassScreen(
                    issuedByUsername = user.username,
                    siteName = state.siteName,
                    viewModel = issueVm,
                    onBack = {
                        admin.refreshDashboard()
                        navController.popBackStack()
                    }
                )
            }

            composable(Routes.PASSES) {
                PassesScreen(viewModel = admin, onBack = { navController.popBackStack() })
            }

            composable(Routes.REPORTS) {
                ReportsScreen(viewModel = admin, onBack = { navController.popBackStack() })
            }

            composable(Routes.USERS) {
                UsersScreen(viewModel = admin, onBack = { navController.popBackStack() })
            }

            composable(Routes.EVENTS) {
                EventsScreen(viewModel = admin, onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = admin,
                    onBack = { navController.popBackStack() },
                    onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) }
                )
            }

            composable(Routes.CHANGE_PASSWORD) {
                ChangePasswordScreen(
                    state = state,
                    viewModel = session,
                    forced = false,
                    onDone = { navController.popBackStack() }
                )
            }
        } else {
            composable(Routes.GUARD_HOME) {
                GuardHomeScreen(
                    displayName = user.displayName,
                    siteName = state.siteName,
                    onEntry = { navController.navigate(Routes.SCAN_ENTRY) },
                    onExit = { navController.navigate(Routes.SCAN_EXIT) },
                    onSignOut = session::signOut
                )
            }
        }

        composable(Routes.SCAN_ENTRY) {
            val scanVm: ScanViewModel = viewModel(
                key = Routes.SCAN_ENTRY,
                factory = ScanViewModel.factory(container, ScanType.ENTRADA)
            )
            ScanScreen(
                type = ScanType.ENTRADA,
                username = user.username,
                viewModel = scanVm,
                onBack = {
                    admin.refreshDashboard()
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SCAN_EXIT) {
            val scanVm: ScanViewModel = viewModel(
                key = Routes.SCAN_EXIT,
                factory = ScanViewModel.factory(container, ScanType.SALIDA)
            )
            ScanScreen(
                type = ScanType.SALIDA,
                username = user.username,
                viewModel = scanVm,
                onBack = {
                    admin.refreshDashboard()
                    navController.popBackStack()
                }
            )
        }
    }
}
