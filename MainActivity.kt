package iq.school.manager
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import iq.school.manager.ui.staff.StaffFileScreen
import iq.school.manager.ui.theme.Navy
import kotlinx.coroutines.delay
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import android.view.MotionEvent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import iq.school.manager.data.SettingDao
import iq.school.manager.domain.Session
import iq.school.manager.ui.setup.SetupScreen
import iq.school.manager.ui.users.UsersScreen
import iq.school.manager.ui.studentattendance.StudentAttendanceScreen
import javax.inject.Inject
import iq.school.manager.ui.allowances.AllowancesScreen
import iq.school.manager.ui.auth.LoginScreen
import iq.school.manager.ui.grades.GradesScreen
import iq.school.manager.ui.grades.PrimaryGradesScreen
import iq.school.manager.ui.notifications.NotificationsScreen
import iq.school.manager.ui.reports.ReportsScreen
import iq.school.manager.ui.search.SearchScreen
import iq.school.manager.ui.records.EnrollmentIndexScreen
import iq.school.manager.ui.records.SettingsScreen
import iq.school.manager.ui.records.TransfersScreen
import iq.school.manager.ui.attendance.AttendanceScreen
import iq.school.manager.ui.dashboard.DashboardScreen
import iq.school.manager.ui.staff.StaffScreen
import iq.school.manager.ui.students.StudentFileScreen
import iq.school.manager.ui.students.StudentsScreen
import iq.school.manager.ui.theme.SchoolTheme

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
  @Inject lateinit var session: Session
  override fun dispatchTouchEvent(ev: MotionEvent): Boolean { session.lastActive = System.currentTimeMillis(); return super.dispatchTouchEvent(ev) }
  override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { SchoolTheme { AppNav() } } }
}
@Composable fun AppNav() {
  val nav = rememberNavController()
  val app: AppViewModel = hiltViewModel()
  LaunchedEffect(Unit) { while (true) { delay(15_000); val min = app.autoLogoutMin()
    if (min > 0 && app.session.user != null && System.currentTimeMillis() - app.session.lastActive > min * 60_000L) { app.session.user = null; nav.navigate("login") { popUpTo(0) } } } }
  NavHost(nav, startDestination = "splash") {
    composable("splash") {
      LaunchedEffect(Unit) { delay(1200); nav.navigate("login") { popUpTo("splash") { inclusive = true } } }
      Box(Modifier.fillMaxSize().background(Navy), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🏫", fontSize = 56.sp); Text("إدارة مدرستي", color = Color.White, style = MaterialTheme.typography.headlineMedium); Text("نظام الإدارة المدرسية الذكي", color = Color.White.copy(alpha = 0.75f)) } }
    }
    composable("login") { LoginScreen(onDone = { nav.navigate("dashboard") { popUpTo("login") { inclusive = true } } }) }
    composable("dashboard") { DashboardScreen(onNavigate = { nav.navigate(it) }) }
    composable("attendance") { AttendanceScreen(onBack = { nav.popBackStack() }, onReport = { nav.navigate("reports") }) }
    composable("users") { UsersScreen() }
    composable("studentAttendance") { StudentAttendanceScreen() }
    composable("setup") { SetupScreen() }
    composable("staff") { StaffScreen(onOpen = { nav.navigate("staffFile/$it") }) }
    composable("staffFile/{id}", listOf(navArgument("id") { type = NavType.LongType })) { StaffFileScreen(onDeleted = { nav.popBackStack() }) }
    composable("allowances") { AllowancesScreen() }
    composable("students") { StudentsScreen(onOpen = { nav.navigate("student/$it") }) }
    composable("student/{id}", listOf(navArgument("id") { type = NavType.LongType })) { StudentFileScreen() }
    composable("enrollmentIndex") { EnrollmentIndexScreen() }
    composable("grades") { GradesScreen() }
    composable("primary") { PrimaryGradesScreen() }
    composable("transfers") { TransfersScreen() }
    composable("settings") { SettingsScreen(onLogout = { nav.navigate("login") { popUpTo(0) } }) }
    composable("reports") { ReportsScreen() }
    composable("search") { SearchScreen(onOpenStudent = { nav.navigate("student/$it") }) }
    composable("notifications") { NotificationsScreen() }

  }
}

@HiltViewModel class AppViewModel @Inject constructor(val session: Session, private val settings: SettingDao) : ViewModel() {
  suspend fun autoLogoutMin(): Int = settings.get("auto_logout_min")?.toIntOrNull() ?: 0
}
