package iq.school.manager.ui.setup
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
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel class SetupViewModel @Inject constructor(school: SchoolDao, private val classes: ClassDao, private val audit: AuditLogger) : ViewModel() {
  private fun <T> Flow<T>.keep(i: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), i)
  val year = school.observe().map { it?.currentYear ?: "" }.keep("")
  val classList = classes.classes().keep(emptyList()); val sections = year.flatMapLatest { classes.sectionRows(it) }.keep(emptyList()); val subjects = classes.subjects().keep(emptyList())
  fun addSection(classId: Long, name: String) { viewModelScope.launch { classes.upsertSection(SectionEntity(classId = classId, name = name.trim(), year = year.value)); audit.log("add", "Sections") } }
  fun addSubject(name: String) { viewModelScope.launch { classes.upsertSubject(SubjectEntity(name = name.trim())); audit.log("add", "Subjects") } }
}
@Composable fun SetupScreen(vm: SetupViewModel = hiltViewModel()) {
  val year by vm.year.collectAsStateWithLifecycle(); val classList by vm.classList.collectAsStateWithLifecycle(); val sections by vm.sections.collectAsStateWithLifecycle(); val subjects by vm.subjects.collectAsStateWithLifecycle()
  var cls by remember { mutableStateOf<Long?>(null) }; var secName by remember { mutableStateOf("") }; var subName by remember { mutableStateOf("") }
  LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item { Text("الشعب والمواد", style = MaterialTheme.typography.titleLarge); Text("السنة الدراسية: $year", style = MaterialTheme.typography.bodySmall) }
    item { Text("الشعب", style = MaterialTheme.typography.titleMedium); Text(sections.joinToString("، ") { it.label }.ifBlank { "لا توجد شعب" }) }
    item { ChipRow(classList.map { it.id to it.name }, cls) { cls = it } }
    item { OutlinedTextField(secName, { secName = it }, Modifier.fillMaxWidth(), label = { Text("اسم الشعبة الجديدة (ج)") }, singleLine = true) }
    item { Button({ vm.addSection(cls!!, secName); secName = "" }, Modifier.fillMaxWidth(), enabled = cls != null && secName.isNotBlank()) { Text("إضافة شعبة") } }
    item { Text("المواد", style = MaterialTheme.typography.titleMedium); Text(subjects.joinToString("، ") { it.name }.ifBlank { "لا توجد مواد" }) }
    item { OutlinedTextField(subName, { subName = it }, Modifier.fillMaxWidth(), label = { Text("اسم المادة الجديدة") }, singleLine = true) }
    item { Button({ vm.addSubject(subName); subName = "" }, Modifier.fillMaxWidth(), enabled = subName.isNotBlank()) { Text("إضافة مادة") } }
  }
}
