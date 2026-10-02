package iq.school.manager.ui.allowances
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iq.school.manager.data.*
import iq.school.manager.domain.AuditLogger
import iq.school.manager.domain.DueStatus
import iq.school.manager.domain.dueStatus
import iq.school.manager.ui.common.ChipRow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel class AllowancesViewModel @Inject constructor(private val allowances: AllowanceDao, private val promotions: PromotionDao, staffDao: StaffDao, private val audit: AuditLogger) : ViewModel() {
  private fun <T> Flow<T>.keep(i: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), i)
  val rows = allowances.rows().keep(emptyList()); val promos = promotions.rows().keep(emptyList()); val staff = staffDao.observeAll().keep(emptyList())
  /** كل إضافة تُحفظ كسجل جديد (يبقى التاريخ)، والعرض والتنبيهات تعتمد على آخر سجل لكل موظف. */
  fun addAllowance(staffId: Long, last: String?, next: String?) { viewModelScope.launch { allowances.upsert(AllowanceEntity(staffId = staffId, lastDate = last, nextDate = next)); audit.log("add", "Allowances", staffId) } }
  fun addPromotion(staffId: Long, from: Int?, to: Int?, last: String?, next: String?) { viewModelScope.launch { promotions.upsert(PromotionEntity(staffId = staffId, fromGrade = from, toGrade = to, lastDate = last, nextDate = next)); audit.log("add", "Promotions", staffId) } }
}
private fun parse(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim()) }.getOrNull()
private fun badge(next: String?): Pair<String, Color> = when (dueStatus(next?.let { parse(it) })) {
  DueStatus.DUE -> "حان الموعد" to Color(0xFFC0392B); DueStatus.NEAR -> "اقترب الموعد" to Color(0xFFD9822B); DueStatus.LATER -> "مستحق لاحقاً" to Color(0xFF2E7D5B); null -> "غير محدد" to Color.Gray }

@Composable fun AllowancesScreen(vm: AllowancesViewModel = hiltViewModel()) {
  val rows by vm.rows.collectAsStateWithLifecycle(); val promos by vm.promos.collectAsStateWithLifecycle(); val staff by vm.staff.collectAsStateWithLifecycle()
  var promoTab by remember { mutableStateOf(false) }; var adding by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("العلاوات والترفيعات", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(!promoTab, { promoTab = false }, { Text("العلاوات") }); FilterChip(promoTab, { promoTab = true }, { Text("الترفيعات") }) }
    LazyColumn(Modifier.weight(1f)) {
      if (!promoTab) { if (rows.isEmpty()) item { Text("لا توجد علاوات مسجلة بعد.") }
        items(rows) { r -> val (label, color) = badge(r.nextDate); Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Column(Modifier.weight(1f)) { Text(r.staffName); Text("آخر علاوة: ${r.lastDate ?: "-"} · القادمة: ${r.nextDate ?: "-"}", style = MaterialTheme.typography.bodySmall) }; Text(label, color = color) }; HorizontalDivider() }
      } else { if (promos.isEmpty()) item { Text("لا توجد ترفيعات مسجلة بعد.") }
        items(promos) { r -> val (label, color) = badge(r.nextDate); Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Column(Modifier.weight(1f)) { Text(r.staffName); Text("من ${r.fromGrade ?: "-"} إلى ${r.toGrade ?: "-"} · آخر ترفيع: ${r.lastDate ?: "-"} · القادم: ${r.nextDate ?: "-"}", style = MaterialTheme.typography.bodySmall) }; Text(label, color = color) }; HorizontalDivider() } }
    }
    Button({ adding = true }, Modifier.fillMaxWidth()) { Text(if (promoTab) "إضافة ترفيع" else "إضافة علاوة") }
  }
  if (adding) AddDialog(promoTab, staff, { adding = false }) { sid, last, next, from, to ->
    if (promoTab) vm.addPromotion(sid, from, to, last, next) else vm.addAllowance(sid, last, next ?: last?.let { LocalDate.parse(it).plusYears(1).toString() }); adding = false }
}
@Composable private fun AddDialog(promo: Boolean, staff: List<StaffEntity>, onDismiss: () -> Unit, onSave: (Long, String?, String?, Int?, Int?) -> Unit) {
  var sid by remember { mutableStateOf<Long?>(null) }; var last by remember { mutableStateOf("") }; var next by remember { mutableStateOf("") }; var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }
  val lastD = parse(last); val nextD = parse(next); val bad = (last.isNotBlank() && lastD == null) || (next.isNotBlank() && nextD == null)
  AlertDialog(onDismiss, title = { Text(if (promo) "إضافة ترفيع" else "إضافة علاوة") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    if (staff.isEmpty()) Text("أضف موظفين أولاً من سجل الملاك.") else ChipRow(staff.map { it.id to it.fullName }, sid) { sid = it }
    OutlinedTextField(last, { last = it }, label = { Text("تاريخ آخر ${if (promo) "ترفيع" else "علاوة"} (2026-10-02)") }, singleLine = true, isError = last.isNotBlank() && lastD == null)
    OutlinedTextField(next, { next = it }, label = { Text(if (promo) "تاريخ الترفيع القادم" else "تاريخ العلاوة القادمة (فارغ = بعد سنة)") }, singleLine = true, isError = next.isNotBlank() && nextD == null)
    if (promo) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(from, { from = it }, Modifier.weight(1f), label = { Text("من درجة") }, singleLine = true); OutlinedTextField(to, { to = it }, Modifier.weight(1f), label = { Text("إلى درجة") }, singleLine = true) }
    if (bad) Text("صيغة التاريخ: سنة-شهر-يوم", color = MaterialTheme.colorScheme.error) } },
    confirmButton = { TextButton({ onSave(sid!!, lastD?.toString(), nextD?.toString(), from.toIntOrNull(), to.toIntOrNull()) }, enabled = sid != null && !bad) { Text("حفظ") } }, dismissButton = { TextButton(onDismiss) { Text("إلغاء") } })
}
