package iq.school.manager.ui.attendance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iq.school.manager.R
import iq.school.manager.data.*
import iq.school.manager.domain.AuditLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel class AttendanceViewModel @Inject constructor(staffDao: StaffDao, private val dao: AttendanceDao, private val audit: AuditLogger) : ViewModel() {
  val date: String = LocalDate.now().toString()
  val staff = staffDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val saved = dao.forDate(date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val draft = MutableStateFlow<Map<Long, AttendanceEntity>>(emptyMap())
  var message by mutableStateOf<String?>(null)
  private fun base(id: Long) = draft.value[id] ?: saved.value.firstOrNull { it.staffId == id } ?: AttendanceEntity(staffId = id, date = date, status = "present")
  fun entity(id: Long) = draft.value[id] ?: saved.value.firstOrNull { it.staffId == id }
  /** عند اختيار «حاضر» أو «متأخر» يُسجَّل وقت الحضور تلقائياً إن لم يكن مسجلاً. */
  fun setStatus(id: Long, s: String) { val b = base(id)
    val ci = b.checkIn ?: if (s == "present" || s == "late") LocalTime.now().truncatedTo(ChronoUnit.MINUTES).toString() else null
    draft.update { it + (id to b.copy(status = s, checkIn = ci)) } }
  fun setDetails(id: Long, ci: String, co: String, reason: String, notes: String) { val b = base(id)
    draft.update { it + (id to b.copy(checkIn = ci.ifBlank { null }, checkOut = co.ifBlank { null }, reason = reason.ifBlank { null }, notes = notes.ifBlank { null })) } }
  fun markAll() { staff.value.forEach { setStatus(it.id, "present") } }
  fun save() { viewModelScope.launch { dao.upsertAll(draft.value.values.toList()); audit.log("add", "Attendance"); draft.value = emptyMap(); message = "تم حفظ الحضور" } }
}
@Composable fun AttendanceScreen(onBack: () -> Unit, onReport: () -> Unit, vm: AttendanceViewModel = hiltViewModel()) {
  val staff by vm.staff.collectAsStateWithLifecycle(); val saved by vm.saved.collectAsStateWithLifecycle(); val draft by vm.draft.collectAsStateWithLifecycle()
  val options = listOf("present" to R.string.present, "absent" to R.string.absent, "late" to R.string.late, "leave" to R.string.leave)
  var detailFor by remember { mutableStateOf<StaffEntity?>(null) }
  Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text(stringResource(R.string.teachers_attendance), style = MaterialTheme.typography.titleLarge); Text(vm.date)
    if (staff.isEmpty()) Text(stringResource(R.string.empty_staff), Modifier.padding(top = 24.dp))
    LazyColumn(Modifier.weight(1f)) {
      items(staff, key = { it.id }) { p ->
        val e = draft[p.id] ?: saved.firstOrNull { it.staffId == p.id }
        Column(Modifier.padding(vertical = 8.dp)) {
          Row { Text(p.fullName, Modifier.weight(1f)); TextButton({ detailFor = p }) { Text("تفاصيل") } }
          if (e != null) Text(listOfNotNull(e.checkIn?.let { "حضور $it" }, e.checkOut?.let { "انصراف $it" }, e.reason?.let { "السبب: $it" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { options.forEach { (code, label) -> FilterChip(e?.status == code, { vm.setStatus(p.id, code) }, { Text(stringResource(label)) }) } }
        }
      }
    }
    vm.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    OutlinedButton(vm::markAll, Modifier.fillMaxWidth()) { Text(stringResource(R.string.mark_all)) }
    OutlinedButton(onReport, Modifier.fillMaxWidth()) { Text("طباعة التقرير") }
    Button(vm::save, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = draft.isNotEmpty()) { Text(stringResource(R.string.save)) }
  }
  detailFor?.let { p -> val e = vm.entity(p.id)
    var ci by remember { mutableStateOf(e?.checkIn ?: "") }; var co by remember { mutableStateOf(e?.checkOut ?: "") }; var reason by remember { mutableStateOf(e?.reason ?: "") }; var notes by remember { mutableStateOf(e?.notes ?: "") }
    AlertDialog({ detailFor = null }, title = { Text(p.fullName) }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      OutlinedTextField(ci, { ci = it }, label = { Text("وقت الحضور (08:00)") }, singleLine = true); OutlinedTextField(co, { co = it }, label = { Text("وقت الانصراف (14:00)") }, singleLine = true)
      OutlinedTextField(reason, { reason = it }, label = { Text("سبب الغياب") }, singleLine = true); OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, singleLine = true) } },
      confirmButton = { TextButton({ vm.setDetails(p.id, ci, co, reason, notes); detailFor = null }) { Text("حفظ") } }, dismissButton = { TextButton({ detailFor = null }) { Text("إلغاء") } }) }
}
