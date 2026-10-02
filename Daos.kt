package iq.school.manager.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface SchoolDao { @Query("SELECT * FROM Schools WHERE id = 1") fun observe(): Flow<SchoolEntity?>; @Upsert suspend fun upsert(s: SchoolEntity) }
@Dao interface UserDao {
  @Query("SELECT * FROM Users ORDER BY role, username") fun all(): Flow<List<UserEntity>>
  @Insert suspend fun insert(u: UserEntity): Long
  @Query("SELECT * FROM Users WHERE username = :u AND isActive = 1") suspend fun byUsername(u: String): UserEntity?
  @Query("SELECT COUNT(*) FROM Users") suspend fun count(): Int
  @Query("SELECT * FROM Users WHERE id = :id AND isActive = 1") suspend fun byId(id: Long): UserEntity?
  @Upsert suspend fun upsert(u: UserEntity)
}
@Dao interface PermissionDao { @Query("SELECT * FROM Permissions WHERE userId = :id") suspend fun forUser(id: Long): List<PermissionEntity>; @Upsert suspend fun upsert(p: PermissionEntity) }

@Dao interface StaffDao {
  @Query("SELECT * FROM Staff WHERE deletedAt IS NULL ORDER BY fullName") fun observeAll(): Flow<List<StaffEntity>>
  @Query("SELECT COUNT(*) FROM Staff WHERE deletedAt IS NULL") fun count(): Flow<Int>
  @Query("SELECT * FROM Staff WHERE deletedAt IS NULL AND (fullName LIKE '%' || :q || '%' OR employeeNo LIKE :q || '%') ORDER BY fullName LIMIT 30") fun search(q: String): Flow<List<StaffEntity>>
  @Query("SELECT * FROM Staff WHERE id = :id") fun byId(id: Long): Flow<StaffEntity?>
  @Query("UPDATE Staff SET deletedAt = :at WHERE id = :id") suspend fun softDelete(id: Long, at: String)
  @Insert suspend fun insert(s: StaffEntity): Long
  @Upsert suspend fun upsert(s: StaffEntity)
}
@Dao interface StudentDao {
  @Query("SELECT COUNT(*) FROM Students WHERE deletedAt IS NULL") fun count(): Flow<Int>
  @Query("SELECT COUNT(*) FROM Students WHERE deletedAt IS NULL AND (guardianName IS NULL OR phone IS NULL)") suspend fun incompleteCount(): Int
  @Query("SELECT * FROM Students WHERE deletedAt IS NULL AND fullName LIKE '%' || :q || '%' ORDER BY fullName LIMIT 100") fun search(q: String): Flow<List<StudentEntity>>
  @Query("SELECT * FROM Students WHERE id = :id") fun byId(id: Long): Flow<StudentEntity?>
  @Query("UPDATE Students SET deletedAt = :at WHERE id = :id") suspend fun softDelete(id: Long, at: String)
  @Insert suspend fun insert(s: StudentEntity): Long
  @Upsert suspend fun upsert(s: StudentEntity)
}
@Dao interface ClassDao {
  @Query("SELECT * FROM Classes ORDER BY level") fun classes(): Flow<List<ClassEntity>>
  @Query("SELECT * FROM Sections WHERE year = :year ORDER BY classId, name") fun sections(year: String): Flow<List<SectionEntity>>
  @Query("SELECT COUNT(*) FROM Sections WHERE year = :year") fun sectionCount(year: String): Flow<Int>
  @Query("SELECT * FROM Subjects ORDER BY name") fun subjects(): Flow<List<SubjectEntity>>
  @Query("SELECT sec.id AS id, c.name || ' - ' || sec.name AS label, c.level AS level FROM Sections sec JOIN Classes c ON c.id = sec.classId WHERE sec.year = :year ORDER BY c.level, sec.name") fun sectionRows(year: String): Flow<List<SectionRow>>
  @Upsert suspend fun upsertClass(c: ClassEntity); @Upsert suspend fun upsertSection(s: SectionEntity); @Upsert suspend fun upsertSubject(s: SubjectEntity)
}
@Dao interface EnrollmentDao {
  @Query("SELECT COALESCE(MAX(CAST(enrollmentNo AS INTEGER)), 0) + 1 FROM Enrollment") suspend fun nextNumber(): Long
  @Query("SELECT * FROM Enrollment WHERE studentId = :id ORDER BY year DESC") fun forStudent(id: Long): Flow<List<EnrollmentEntity>>
  @Query("SELECT * FROM EnrollmentIndex WHERE fullName LIKE '%' || :q || '%' OR enrollmentNo LIKE :q || '%' ORDER BY fullName") fun indexByName(q: String): Flow<List<EnrollmentIndexRow>>
  @Query("SELECT * FROM EnrollmentIndex WHERE fullName LIKE '%' || :q || '%' OR enrollmentNo LIKE :q || '%' ORDER BY CAST(enrollmentNo AS INTEGER)") fun indexByNumber(q: String): Flow<List<EnrollmentIndexRow>>
  @Insert suspend fun insert(e: EnrollmentEntity): Long
  @Query("SELECT e.id AS enrollmentId, s.fullName AS fullName FROM Enrollment e JOIN Students s ON s.id = e.studentId WHERE e.sectionId = :sec AND s.deletedAt IS NULL AND e.status = 'enrolled' ORDER BY s.fullName") fun roster(sec: Long): Flow<List<RosterRow>>
  @Query("UPDATE Enrollment SET status = 'transferred' WHERE studentId = :id AND status = 'enrolled'") suspend fun markTransferred(id: Long)
  @Query("SELECT COUNT(*) FROM Enrollment e WHERE e.status = 'enrolled' AND NOT EXISTS (SELECT 1 FROM Grades g WHERE g.enrollmentId = e.id) AND NOT EXISTS (SELECT 1 FROM PrimaryGrades p WHERE p.enrollmentId = e.id)") suspend fun withoutGradesCount(): Int
}
@Dao interface AttendanceDao {
  @Query("SELECT * FROM Attendance WHERE date = :date") fun forDate(date: String): Flow<List<AttendanceEntity>>
  @Query("SELECT * FROM Attendance WHERE date BETWEEN :from AND :to ORDER BY date") suspend fun range(from: String, to: String): List<AttendanceEntity>
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<AttendanceEntity>)
}
@Dao interface StudentAttendanceDao {
  @Query("SELECT sa.* FROM StudentAttendance sa JOIN Enrollment e ON e.id = sa.enrollmentId WHERE e.sectionId = :sec AND sa.date = :date") fun forSectionDate(sec: Long, date: String): Flow<List<StudentAttendanceEntity>>
  @Query("SELECT sa.status AS status, COUNT(*) AS n FROM StudentAttendance sa JOIN Enrollment e ON e.id = sa.enrollmentId WHERE e.studentId = :studentId GROUP BY sa.status") fun counts(studentId: Long): Flow<List<StatusCount>>
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(r: List<StudentAttendanceEntity>)
}
@Dao interface DocumentDao {
  @Query("SELECT * FROM StudentDocuments WHERE studentId = :id ORDER BY addedAt DESC") fun forStudent(id: Long): Flow<List<DocumentEntity>>
  @Insert suspend fun insert(d: DocumentEntity): Long
  @Delete suspend fun delete(d: DocumentEntity)
}

data class AllowanceRow(val staffName: String, val lastDate: String?, val nextDate: String?)
@Dao interface AllowanceDao {
  @Query("SELECT st.fullName AS staffName, a.lastDate AS lastDate, a.nextDate AS nextDate FROM Allowances a JOIN Staff st ON st.id = a.staffId WHERE st.deletedAt IS NULL AND a.id IN (SELECT MAX(id) FROM Allowances GROUP BY staffId) ORDER BY a.nextDate")
  fun rows(): Flow<List<AllowanceRow>>
  @Query("SELECT COUNT(*) FROM Allowances WHERE nextDate IS NOT NULL AND nextDate <= :date AND id IN (SELECT MAX(id) FROM Allowances GROUP BY staffId)") suspend fun dueCount(date: String): Int
  @Query("SELECT COUNT(*) FROM Allowances WHERE nextDate BETWEEN :from AND :to AND id IN (SELECT MAX(id) FROM Allowances GROUP BY staffId)") suspend fun nearCount(from: String, to: String): Int
  @Upsert suspend fun upsert(a: AllowanceEntity)
}
@Dao interface PromotionDao {
  @Query("SELECT st.fullName AS staffName, p.fromGrade AS fromGrade, p.toGrade AS toGrade, p.lastDate AS lastDate, p.nextDate AS nextDate FROM Promotions p JOIN Staff st ON st.id = p.staffId WHERE st.deletedAt IS NULL AND p.id IN (SELECT MAX(id) FROM Promotions GROUP BY staffId) ORDER BY p.nextDate") fun rows(): Flow<List<PromotionRow>>
  @Upsert suspend fun upsert(p: PromotionEntity)
}
@Dao interface TransferDao {
  @Query("SELECT t.date AS date, s.fullName AS studentName, t.fromSchool AS fromSchool, t.toSchool AS toSchool, t.reason AS reason, t.letterNo AS letterNo FROM Transfers t JOIN Students s ON s.id = t.studentId ORDER BY t.date DESC") fun rows(): Flow<List<TransferRow>>
  @Insert suspend fun insert(t: TransferEntity): Long
}

@Dao interface GradeDao {
  @Query("SELECT * FROM Grades WHERE enrollmentId = :id") fun forEnrollment(id: Long): Flow<List<GradeEntity>>
  @Query("SELECT g.* FROM Grades g JOIN Enrollment e ON e.id = g.enrollmentId WHERE e.sectionId = :sec AND g.subjectId = :subj") fun forSectionSubject(sec: Long, subj: Long): Flow<List<GradeEntity>>
  @Query("SELECT g.* FROM Grades g JOIN Enrollment e ON e.id = g.enrollmentId WHERE e.sectionId = :sec") fun forSection(sec: Long): Flow<List<GradeEntity>>
  @Query("SELECT s.fullName AS studentName, sub.name AS subjectName, g.month1 AS month1, g.month2 AS month2, g.midyear AS midyear, g.month3 AS month3, g.month4 AS month4 FROM Grades g JOIN Enrollment e ON e.id = g.enrollmentId JOIN Students s ON s.id = e.studentId JOIN Subjects sub ON sub.id = g.subjectId WHERE s.deletedAt IS NULL AND s.fullName LIKE '%' || :q || '%' LIMIT 30") fun byStudentName(q: String): Flow<List<GradeHit>>
  @Upsert suspend fun upsert(g: GradeEntity)
}
@Dao interface PrimaryGradeDao {
  @Query("SELECT * FROM PrimaryGrades WHERE enrollmentId = :id") fun forEnrollment(id: Long): Flow<List<PrimaryGradeEntity>>
  @Query("SELECT g.* FROM PrimaryGrades g JOIN Enrollment e ON e.id = g.enrollmentId WHERE e.sectionId = :sec AND g.subjectId = :subj AND g.term = :term") fun forSectionSubjectTerm(sec: Long, subj: Long, term: String): Flow<List<PrimaryGradeEntity>>
  @Upsert suspend fun upsert(g: PrimaryGradeEntity)
}
@Dao interface ReportDao { @Insert suspend fun insert(r: ReportEntity): Long }
@Dao interface NotificationDao {
  @Query("SELECT * FROM Notifications ORDER BY createdAt DESC LIMIT 50") fun recent(): Flow<List<NotificationEntity>>
  @Query("SELECT COUNT(*) FROM Notifications WHERE isRead = 0") fun unread(): Flow<Int>
  @Insert suspend fun insert(n: NotificationEntity)
  @Query("DELETE FROM Notifications WHERE type LIKE 'auto_%'") suspend fun clearAuto()
}
@Dao interface AuditDao {
  @Insert suspend fun insert(a: AuditLogEntity)
  @Query("SELECT * FROM AuditLogs ORDER BY at DESC LIMIT :n") fun recent(n: Int): Flow<List<AuditLogEntity>>
}
@Dao interface SettingDao {
  @Query("SELECT value FROM Settings WHERE `key` = :k") suspend fun get(k: String): String?
  @Upsert suspend fun put(s: SettingEntity)
  @Query("DELETE FROM Settings WHERE `key` = :k") suspend fun remove(k: String)
}
@Dao interface BackupDao { @Query("SELECT * FROM Backups ORDER BY createdAt DESC") fun all(): Flow<List<BackupEntity>>; @Insert suspend fun insert(b: BackupEntity) }

data class SectionRow(val id: Long, val label: String, val level: Int)
data class RosterRow(val enrollmentId: Long, val fullName: String)
data class TransferRow(val date: String, val studentName: String, val fromSchool: String?, val toSchool: String?, val reason: String?, val letterNo: String?)
data class PromotionRow(val staffName: String, val fromGrade: Int?, val toGrade: Int?, val lastDate: String?, val nextDate: String?)
data class GradeHit(val studentName: String, val subjectName: String, val month1: Double?, val month2: Double?, val midyear: Double?, val month3: Double?, val month4: Double?)
data class StatusCount(val status: String, val n: Int)
