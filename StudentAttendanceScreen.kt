package iq.school.manager.ui.studentattendance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import iq.school.manager.domain.AuditLogger
import iq.school.manager.ui.common.ChipRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel class StudentAttendanceViewModel @Inject constructor(school: SchoolDao, classes: ClassDao, enrollment: EnrollmentDao, private val dao: StudentAttendanceDao, private val audit: AuditLogger) : ViewModel() {
  private fun <T> Flow<T>.keep(i: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), i)
  val date: String = LocalDate.now().toString()
  val sections = school.observe().flatMapLatest { classes.sectionRows(it?.currentYear ?: "") }.keep(emptyList())
  val section = MutableStateFlow<Long?>(null)
  val roster = section.flatMapLatest { s -> if (s == null) flowOf(emptyList()) else enrollment.roster(s) }.keep(emptyList())
  val saved = section.flatMapLatest { s -> if (s == null) flowOf(emptyList()) else dao.forSectionDate(s, date) }.keep(emptyList())
  val draft = MutableStateFlow<Map<Long, String>>(emptyMap()); var message by mutableStateOf<String?>(null)
  fun select(id: Long) { section.value = id; draft.value = emptyMap(); message = null }
  fun set(eid: Long, s: String) = draft.update { it + (eid to s) }
  fun markAll() = draft.update { roster.value.associate { it.enrollmentId to "present" } }
  fun save() { viewModelScope.launch { dao.upsertAll(draft.value.map { (eid, s) -> StudentAttendanceEntity(enrollmentId = eid, date = date, status = s) }); audit.log("add", "StudentAttendance"); draft.value = emptyMap(); message = "تم حفظ الحضور" } }
}
@Composable fun StudentAttendanceScreen(vm: StudentAttendanceViewModel = hiltViewModel()) {
  val sections by vm.sections.collectAsStateWithLifecycle(); val sec by vm.section.collectAsStateWithLifecycle(); val roster by vm.roster.collectAsStateWithLifecycle()
  val saved by vm.saved.collectAsStateWithLifecycle(); val draft by vm.draft.collectAsStateWithLifecycle()
  val options = listOf("present" to "حاضر", "absent" to "غائب", "late" to "متأخر", "leave" to "إجازة"); val byEnroll = saved.associateBy { it.enrollmentId }
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("حضور الطلاب", style = MaterialTheme.typography.titleLarge); Text(vm.date)
    ChipRow(sections.map { it.id to it.label }, sec) { vm.select(it) }
    if (sec == null) Text("اختر الشعبة.") else if (roster.isEmpty()) Text("لا يوجد طلاب مقيدون في هذه الشعبة.")
    LazyColumn(Modifier.weight(1f)) { items(roster, key = { it.enrollmentId }) { r -> val cur = draft[r.enrollmentId] ?: byEnroll[r.enrollmentId]?.status
      Column(Modifier.padding(vertical = 6.dp)) { Text(r.fullName); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { options.forEach { (c, l) -> FilterChip(cur == c, { vm.set(r.enrollmentId, c) }, { Text(l) }) } } }; HorizontalDivider() } }
    vm.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    OutlinedButton(vm::markAll, Modifier.fillMaxWidth(), enabled = roster.isNotEmpty()) { Text("تسجيل حضور الجميع") }
    Button(vm::save, Modifier.fillMaxWidth(), enabled = draft.isNotEmpty()) { Text("حفظ") }
  }
}
