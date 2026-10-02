package iq.school.manager.ui.staff
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
import iq.school.manager.domain.AuditLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class StaffViewModel @Inject constructor(private val dao: StaffDao, private val audit: AuditLogger) : ViewModel() {
  val staff = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  fun add(name: String, title: String) { viewModelScope.launch { val id = dao.insert(StaffEntity(fullName = name.trim(), jobTitle = title.ifBlank { null })); audit.log("add", "Staff", id) } }
}
@Composable fun StaffScreen(onOpen: (Long) -> Unit, vm: StaffViewModel = hiltViewModel()) {
  val list by vm.staff.collectAsStateWithLifecycle(); var adding by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("معلومات الملاك", style = MaterialTheme.typography.titleLarge)
    if (list.isEmpty()) Text("لا يوجد موظفون بعد. أضف أول موظف.")
    LazyColumn(Modifier.weight(1f)) { items(list, key = { it.id }) { p -> Column(Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(vertical = 8.dp)) { Text(p.fullName); Text(p.jobTitle ?: "", style = MaterialTheme.typography.bodySmall) }; HorizontalDivider() } }
    Button({ adding = true }, Modifier.fillMaxWidth()) { Text("إضافة موظف") }
  }
  if (adding) { var n by remember { mutableStateOf("") }; var t by remember { mutableStateOf("") }
    AlertDialog({ adding = false }, title = { Text("إضافة موظف") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      OutlinedTextField(n, { n = it }, label = { Text("الاسم") }, singleLine = true); OutlinedTextField(t, { t = it }, label = { Text("العنوان الوظيفي") }, singleLine = true) } },
      confirmButton = { TextButton({ vm.add(n, t); adding = false }, enabled = n.isNotBlank()) { Text("حفظ") } }, dismissButton = { TextButton({ adding = false }) { Text("إلغاء") } }) }
}
