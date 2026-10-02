package iq.school.manager.ui.search
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SearchResults(val staff: List<StaffEntity> = emptyList(), val students: List<StudentEntity> = emptyList(), val enrollments: List<EnrollmentIndexRow> = emptyList(),
  val grades: List<GradeHit> = emptyList(), val transfers: List<TransferRow> = emptyList()) { val isEmpty get() = staff.isEmpty() && students.isEmpty() && enrollments.isEmpty() && grades.isEmpty() && transfers.isEmpty() }

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel class SearchViewModel @Inject constructor(staff: StaffDao, students: StudentDao, enrollment: EnrollmentDao, grades: GradeDao, transfers: TransferDao) : ViewModel() {
  val query = MutableStateFlow("")
  val results = query.debounce(200).map { it.trim() }.flatMapLatest { q ->
    if (q.length < 2) flowOf(SearchResults()) else combine(staff.search(q), students.search(q), enrollment.indexByName(q), grades.byStudentName(q), transfers.rows().map { r -> r.filter { it.studentName.contains(q) } }) { a, b, c, d, e -> SearchResults(a, b, c, d, e) }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())
}
@Composable fun SearchScreen(onOpenStudent: (Long) -> Unit, vm: SearchViewModel = hiltViewModel()) {
  val q by vm.query.collectAsStateWithLifecycle(); val r by vm.results.collectAsStateWithLifecycle()
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(q, { vm.query.value = it }, Modifier.fillMaxWidth(), placeholder = { Text("ابحث عن طالب، معلم، سجل...") }, singleLine = true)
    if (q.trim().length < 2) Text("اكتب حرفين على الأقل للبحث في كل السجلات.") else if (r.isEmpty) Text("لا توجد نتائج. جرّب اسماً آخر أو رقم القيد.")
    LazyColumn {
      if (r.staff.isNotEmpty()) { item { Text("👨‍🏫 الموظفون (${r.staff.size})", style = MaterialTheme.typography.titleSmall) }; items(r.staff, key = { "s${it.id}" }) { Text("${it.fullName} · ${it.jobTitle ?: "-"}", Modifier.padding(vertical = 6.dp)) } }
      if (r.students.isNotEmpty()) { item { Text("👨‍🎓 الطلاب (${r.students.size})", style = MaterialTheme.typography.titleSmall) }; items(r.students, key = { "t${it.id}" }) { Text(it.fullName, Modifier.fillMaxWidth().clickable { onOpenStudent(it.id) }.padding(vertical = 8.dp)) } }
      if (r.enrollments.isNotEmpty()) { item { Text("📚 سجلات القيد (${r.enrollments.size})", style = MaterialTheme.typography.titleSmall) }; items(r.enrollments, key = { "e${it.enrollmentNo}" }) { Text("${it.enrollmentNo} · ${it.fullName} · ${it.className} - ${it.sectionName}", Modifier.padding(vertical = 6.dp)) } }
      if (r.grades.isNotEmpty()) { item { Text("📝 الدرجات (${r.grades.size})", style = MaterialTheme.typography.titleSmall) }; items(r.grades) { g -> Text("${g.studentName} · ${g.subjectName}: " + listOf(g.month1, g.month2, g.midyear, g.month3, g.month4).joinToString(" / ") { it?.toString() ?: "-" }, Modifier.padding(vertical = 6.dp)) } }
      if (r.transfers.isNotEmpty()) { item { Text("🔄 الترحيل (${r.transfers.size})", style = MaterialTheme.typography.titleSmall) }; items(r.transfers) { Text("${it.studentName} → ${it.toSchool ?: "-"} · ${it.date}", Modifier.padding(vertical = 6.dp)) } }
    }
  }
}
