package iq.school.manager.ui.dashboard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iq.school.manager.data.*
import iq.school.manager.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel class DashboardViewModel @Inject constructor(staff: StaffDao, students: StudentDao, school: SchoolDao, classes: ClassDao, attendance: AttendanceDao,
  notifications: NotificationDao, audit: AuditDao, private val engine: NotificationEngine, val session: Session) : ViewModel() {
  private fun <T> Flow<T>.keep(i: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), i)
  val staffCount = staff.count(); val studentCount = students.count()
  val schoolName = school.observe().map { it?.name ?: "" }.keep("")
  val sectionCount = school.observe().flatMapLatest { classes.sectionCount(it?.currentYear ?: "") }
  val attendancePct = combine(staffCount, attendance.forDate(LocalDate.now().toString())) { total, rows -> if (total == 0) null else rows.count { it.status == "present" || it.status == "late" } * 100 / total }.keep(null)
  val alerts = notifications.recent().keep(emptyList()); val ops = audit.recent(4).keep(emptyList())
  init { viewModelScope.launch { engine.refresh() } }
}
private val months = listOf("كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران", "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول")
private val days = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
private fun dateAr(d: LocalDate) = "${days[d.dayOfWeek.value - 1]}، ${d.dayOfMonth} ${months[d.monthValue - 1]} ${d.year}"
private fun actionAr(a: String) = mapOf("add" to "إضافة", "delete" to "حذف", "login" to "تسجيل دخول", "enroll" to "قيد طالب", "transfer" to "ترحيل", "grades" to "حفظ درجات",
  "settings" to "تعديل الإعدادات", "backup" to "نسخة احتياطية", "report" to "إنشاء تقرير", "update" to "تعديل بيانات")[a] ?: a

@Composable fun DashboardScreen(onNavigate: (String) -> Unit, vm: DashboardViewModel = hiltViewModel()) {
  val staff by vm.staffCount.collectAsStateWithLifecycle(0); val students by vm.studentCount.collectAsStateWithLifecycle(0); val sections by vm.sectionCount.collectAsStateWithLifecycle(0)
  val pct by vm.attendancePct.collectAsStateWithLifecycle(); val schoolName by vm.schoolName.collectAsStateWithLifecycle()
  val alerts by vm.alerts.collectAsStateWithLifecycle(); val ops by vm.ops.collectAsStateWithLifecycle(); val role = vm.session.role
  val greeting = if (LocalTime.now().hour < 12) "صباح الخير" else "مساء الخير"; val who = if (role == "principal") "أستاذ المدير" else vm.session.user?.username ?: ""
  val services = listOf(Triple("attendance", "حضور المعلمين", "attendance"), Triple("studentAttendance", "حضور الطلاب", "attendance"), Triple("staff", "معلومات الملاك", "staff"), Triple("allowances", "العلاوات والترفيعات", "staff"),
    Triple("students", "الطلاب وملف الطالب", "students"), Triple("enrollmentIndex", "فهرس القيد", "enrollment"), Triple("grades", "درجات الخامس والسادس", "grades"),
    Triple("primary", "درجات الصفوف الأولية", "grades"), Triple("transfers", "سجل الترحيل", "transfers"), Triple("reports", "التقارير", "reports"),
    Triple("users", "المستخدمون والصلاحيات", "users"), Triple("setup", "الشعب والمواد", "settings"), Triple("settings", "الإعدادات والنسخ الاحتياطي", "settings")).filter { Access.canAccess(role, it.third) }
  val quick = listOf(Triple("attendance", "الحضور", "attendance"), Triple("students", "إضافة طالب", "students"), Triple("staff", "إضافة موظف", "staff"), Triple("grades", "إدخال الدرجات", "grades")).filter { Access.canAccess(role, it.third) }
  LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    item { Column { Text("$greeting، $who", style = MaterialTheme.typography.titleLarge); Text("مدرسة " + schoolName.ifBlank { "............" }); Text(dateAr(LocalDate.now()), style = MaterialTheme.typography.bodySmall) } }
    item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("$students", "طالب", Modifier.weight(1f)); Stat("$staff", "معلم وموظف", Modifier.weight(1f)) } }
    item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("$sections", "شعبة", Modifier.weight(1f)); Stat(pct?.let { "$it%" } ?: "-", "حضور اليوم", Modifier.weight(1f)) } }
    item { OutlinedButton({ onNavigate("search") }, Modifier.fillMaxWidth()) { Text("🔎 ابحث عن طالب، معلم، سجل...") } }
    if (quick.isNotEmpty()) item { Text("الوصول السريع", style = MaterialTheme.typography.titleSmall); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { quick.forEach { (r, l, _) -> FilledTonalButton({ onNavigate(r) }) { Text(l) } } } }
    item { Card(Modifier.fillMaxWidth().clickable { onNavigate("notifications") }) { Column(Modifier.padding(14.dp)) { Text("التنبيهات", style = MaterialTheme.typography.titleSmall)
      if (alerts.isEmpty()) Text("لا توجد تنبيهات", style = MaterialTheme.typography.bodySmall) else alerts.take(3).forEach { Text("🔔 " + it.message, style = MaterialTheme.typography.bodySmall) } } } }
    item { Text("الخدمات الرئيسية", style = MaterialTheme.typography.titleSmall) }
    services.forEach { (route, label, _) -> item { Button({ onNavigate(route) }, Modifier.fillMaxWidth()) { Text(label) } } }
    item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("آخر العمليات", style = MaterialTheme.typography.titleSmall)
      if (ops.isEmpty()) Text("لا توجد عمليات بعد", style = MaterialTheme.typography.bodySmall) else ops.forEach { Text(actionAr(it.action) + " · " + it.at.take(16).replace('T', ' '), style = MaterialTheme.typography.bodySmall) } } } }
  }
}
@Composable private fun Stat(value: String, label: String, m: Modifier) = Card(m) { Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium); Text(label, style = MaterialTheme.typography.bodySmall) } }
