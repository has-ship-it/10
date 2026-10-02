package iq.school.manager.ui.staff
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iq.school.manager.data.*
import iq.school.manager.domain.Access
import iq.school.manager.domain.AuditLogger
import iq.school.manager.domain.PdfExporter
import iq.school.manager.domain.ReportData
import iq.school.manager.domain.Session
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel class StaffFileViewModel @Inject constructor(state: SavedStateHandle, private val dao: StaffDao, private val audit: AuditLogger, val session: Session, school: SchoolDao) : ViewModel() {
  private val id: Long = state.get<Long>("id") ?: 0L
  val schoolName = school.observe().map { it?.name ?: "" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
  val staff = dao.byId(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
  var error by mutableStateOf<String?>(null)
  fun update(s: StaffEntity) { viewModelScope.launch { runCatching { dao.upsert(s) }.onSuccess { error = null; audit.log("update", "Staff", s.id) }.onFailure { error = "الرقم الوظيفي مستخدم لموظف آخر" } } }
  fun delete(onDone: () -> Unit) { viewModelScope.launch { dao.softDelete(id, LocalDate.now().toString()); audit.log("delete", "Staff", id); onDone() } }
}
@Composable fun StaffFileScreen(onDeleted: () -> Unit, vm: StaffFileViewModel = hiltViewModel()) {
  val s by vm.staff.collectAsStateWithLifecycle(); val st = s ?: return
  var edit by remember { mutableStateOf(false) }; var confirm by remember { mutableStateOf(false) }
  val lines = listOf("العنوان الوظيفي" to st.jobTitle, "الرقم الوظيفي" to st.employeeNo, "الجنس" to st.gender, "تاريخ التولد" to st.birthDate, "رقم الهوية" to st.nationalId,
    "الدرجة الوظيفية" to st.jobGrade?.toString(), "المرحلة" to st.jobStage?.toString(), "الشهادة" to st.certificate, "الاختصاص" to st.specialization, "تاريخ التعيين" to st.hireDate,
    "تاريخ المباشرة" to st.startDate, "الهاتف" to st.phone, "الحالة الوظيفية" to st.status, "الملاحظات" to st.notes)
  LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item { Text("الملف الوظيفي", style = MaterialTheme.typography.titleLarge); Text(st.fullName, style = MaterialTheme.typography.titleMedium) }
    item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { lines.forEach { (k, v) -> Text("$k: ${v ?: "-"}", style = MaterialTheme.typography.bodyMedium) } } } }
    item { Button({ edit = true }, Modifier.fillMaxWidth()) { Text("تعديل") } }
    item { val ctx = androidx.compose.ui.platform.LocalContext.current; val school by vm.schoolName.collectAsStateWithLifecycle()
      OutlinedButton({ PdfExporter.print(ctx, PdfExporter.write(ctx, ReportData("الملف الوظيفي — " + st.fullName, school, listOf("البيان", "القيمة"), lines.map { listOf(it.first, it.second ?: "-") })), "الملف الوظيفي") }, Modifier.fillMaxWidth()) { Text("طباعة الملف الوظيفي") } }
    if (Access.canDelete(vm.session.role)) item { OutlinedButton({ confirm = true }, Modifier.fillMaxWidth()) { Text("حذف الموظف") } }
  }
  if (edit) EditStaffDialog(st, vm.error, { edit = false }) { vm.update(it); edit = false }
  if (confirm) AlertDialog({ confirm = false }, title = { Text("تأكيد الحذف") }, text = { Text("سيُحذف الموظف «${st.fullName}» من السجلات. هل تريد المتابعة؟") },
    confirmButton = { TextButton({ vm.delete(onDeleted) }) { Text("حذف") } }, dismissButton = { TextButton({ confirm = false }) { Text("إلغاء") } })
}
@Composable private fun F(label: String, v: String, set: (String) -> Unit) = OutlinedTextField(v, set, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
@Composable private fun EditStaffDialog(s: StaffEntity, error: String?, onDismiss: () -> Unit, onSave: (StaffEntity) -> Unit) {
  var name by remember { mutableStateOf(s.fullName) }; var title by remember { mutableStateOf(s.jobTitle ?: "") }; var no by remember { mutableStateOf(s.employeeNo ?: "") }
  var phone by remember { mutableStateOf(s.phone ?: "") }; var cert by remember { mutableStateOf(s.certificate ?: "") }; var spec by remember { mutableStateOf(s.specialization ?: "") }
  var birth by remember { mutableStateOf(s.birthDate ?: "") }; var hire by remember { mutableStateOf(s.hireDate ?: "") }; var start by remember { mutableStateOf(s.startDate ?: "") }
  var grade by remember { mutableStateOf(s.jobGrade?.toString() ?: "") }; var stage by remember { mutableStateOf(s.jobStage?.toString() ?: "") }; var notes by remember { mutableStateOf(s.notes ?: "") }
  AlertDialog(onDismiss, title = { Text("تعديل بيانات الموظف") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    F("الاسم", name) { name = it }; F("العنوان الوظيفي", title) { title = it }; F("الرقم الوظيفي", no) { no = it }; F("الهاتف", phone) { phone = it }; F("الشهادة", cert) { cert = it }; F("الاختصاص", spec) { spec = it }
    F("تاريخ التولد", birth) { birth = it }; F("تاريخ التعيين", hire) { hire = it }; F("تاريخ المباشرة", start) { start = it }; F("الدرجة الوظيفية", grade) { grade = it }; F("المرحلة", stage) { stage = it }; F("الملاحظات", notes) { notes = it }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
    confirmButton = { TextButton({ onSave(s.copy(fullName = name.trim(), jobTitle = title.ifBlank { null }, employeeNo = no.ifBlank { null }, phone = phone.ifBlank { null }, certificate = cert.ifBlank { null }, specialization = spec.ifBlank { null },
      birthDate = birth.ifBlank { null }, hireDate = hire.ifBlank { null }, startDate = start.ifBlank { null }, jobGrade = grade.toIntOrNull(), jobStage = stage.toIntOrNull(), notes = notes.ifBlank { null })) }, enabled = name.isNotBlank()) { Text("حفظ") } },
    dismissButton = { TextButton(onDismiss) { Text("إلغاء") } })
}
