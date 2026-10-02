package iq.school.manager.domain
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate

data class ReportData(val title: String, val school: String, val columns: List<String>, val rows: List<List<String>>)

/** تقارير A4 رسمية: ترويسة باسم المدرسة، جدول RTL (العمود الأول عند اليمين)، ترقيم الصفحات. */
object PdfExporter {
  private const val W = 595; private const val H = 842; private const val M = 36f; private const val ROW = 20f
  fun write(ctx: Context, d: ReportData): File {
    val doc = PdfDocument()
    fun paint(size: Float, bold: Boolean, align: Paint.Align) = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; textAlign = align; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
    val head = paint(15f, true, Paint.Align.CENTER); val sub = paint(11f, false, Paint.Align.CENTER); val th = paint(11f, true, Paint.Align.RIGHT)
    val td = paint(10.5f, false, Paint.Align.RIGHT); val foot = paint(9f, false, Paint.Align.CENTER)
    val rule = Paint().apply { color = Color.GRAY; strokeWidth = 0.6f }
    val colW = (W - 2 * M) / d.columns.size
    fun cut(p: Paint, s: String): String { val k = p.breakText(s, true, colW - 8, null); return if (k < s.length) s.take(maxOf(k - 1, 0)) + "…" else s }
    var page = 0; var y = 0f; lateinit var pg: PdfDocument.Page; lateinit var c: Canvas
    fun rowOut(cells: List<String>, p: Paint) { cells.forEachIndexed { i, s -> c.drawText(cut(p, s), W - M - i * colW - 4, y + 14, p) }; y += ROW; c.drawLine(M, y, W - M, y, rule) }
    fun start() { page++; pg = doc.startPage(PdfDocument.PageInfo.Builder(W, H, page).create()); c = pg.canvas; y = M
      c.drawText(d.school, W / 2f, y + 14, head); y += 24; c.drawText(d.title + " — " + LocalDate.now(), W / 2f, y + 12, sub); y += 30
      c.drawLine(M, y, W - M, y, rule); rowOut(d.columns, th) }
    fun end() { c.drawText("صفحة $page", W / 2f, H - 22f, foot); doc.finishPage(pg) }
    start()
    if (d.rows.isEmpty()) c.drawText("لا توجد بيانات لهذا التقرير", W - M, y + 20, td)
    d.rows.forEach { r -> if (y > H - 60) { end(); start() }; rowOut(r, td) }
    end()
    val f = File(ctx.cacheDir, "reports").apply { mkdirs() }.let { File(it, "report-${System.currentTimeMillis()}.pdf") }
    FileOutputStream(f).use { doc.writeTo(it) }; doc.close(); return f
  }
  fun share(ctx: Context, f: File) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, null))
  }
  fun shareFile(ctx: Context, f: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, null))
  }
  fun viewFile(ctx: Context, f: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
      .onFailure { android.widget.Toast.makeText(ctx, "لا يوجد تطبيق لفتح هذا الملف", android.widget.Toast.LENGTH_LONG).show() }
  }
  fun print(ctx: Context, f: File, name: String) { (ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(name, PdfPrintAdapter(f, name), null) }
}
class PdfPrintAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
  override fun onLayout(o: PrintAttributes?, n: PrintAttributes?, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
    if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
    cb.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true) }
  override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
    try { FileInputStream(file).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }; cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) } catch (e: Exception) { cb.onWriteFailed(e.message) } }
}
