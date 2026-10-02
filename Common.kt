package iq.school.manager.ui.common
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable fun ChipRow(options: List<Pair<Long, String>>, selected: Long?, onSelect: (Long) -> Unit) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(options, key = { it.first }) { (id, label) -> FilterChip(selected == id, { onSelect(id) }, { Text(label) }) } }
}
