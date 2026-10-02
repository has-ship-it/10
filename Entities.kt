package iq.school.manager.data
import androidx.room.*

@Entity(tableName = "Schools") data class SchoolEntity(@PrimaryKey val id: Long = 1, val name: String, val governorate: String? = null,
  val district: String? = null, val subdistrict: String? = null, val stage: String? = null, val principalName: String? = null, val currentYear: String)

@Entity(tableName = "Users", indices = [Index("username", unique = true)]) data class UserEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0, val schoolId: Long = 1, val username: String, val passwordHash: String, val salt: String,
  val role: String, val staffId: Long? = null, val pinHash: String? = null, val biometricEnabled: Boolean = false,
  val autoLogoutMin: Int? = null, val isActive: Boolean = true, val lastLogin: String? = null)

/** scopeSubjectId = 0 يعني كل المواد. */
@Entity(tableName = "Permissions", primaryKeys = ["userId", "module", "scopeSubjectId"]) data class PermissionEntity(
  val userId: Long, val module: String, val scopeSubjectId: Long = 0, val canView: Boolean = false, val canEdit: Boolean = false, val canDelete: Boolean = false)

@Entity(tableName = "Staff", indices = [Index("employeeNo", unique = true)]) data class StaffEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0, val fullName: String, val jobTitle: String? = null, val employeeNo: String? = null,
  val phone: String? = null, val status: String = "active", val gender: String? = null, val birthDate: String? = null, val nationalId: String? = null,
  val jobGrade: Int? = null, val jobStage: Int? = null, val certificate: String? = null, val specialization: String? = null,
  val hireDate: String? = null, val startDate: String? = null, val notes: String? = null, val deletedAt: String? = null)

@Entity(tableName = "Teachers") data class TeacherEntity(@PrimaryKey val staffId: Long, val weeklyPeriods: Int? = null)
@Entity(tableName = "TeacherSubjects", primaryKeys = ["teacherId", "subjectId", "sectionId"]) data class TeacherSubjectEntity(val teacherId: Long, val subjectId: Long, val sectionId: Long)

@Entity(tableName = "Classes", indices = [Index("level", unique = true)]) data class ClassEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val level: Int, val isPrimarySystem: Boolean = false)
@Entity(tableName = "Sections", indices = [Index(value = ["classId", "name", "year"], unique = true)]) data class SectionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0, val classId: Long, val name: String, val year: String)
@Entity(tableName = "Subjects") data class SubjectEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val classId: Long? = null)

@Entity(tableName = "Students", indices = [Index("fullName")]) data class StudentEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0, val fullName: String, val fatherName: String? = null, val grandfatherName: String? = null,
  val motherName: String? = null, val birthDate: String? = null, val birthPlace: String? = null, val gender: String? = null,
  val guardianName: String? = null, val phone: String? = null, val address: String? = null, val idDocNo: String? = null,
  val notes: String? = null, val deletedAt: String? = null)

@Entity(tableName = "Enrollment", indices = [Index("enrollmentNo", unique = true), Index(value = ["studentId", "year"], unique = true), Index("sectionId")],
  foreignKeys = [ForeignKey(StudentEntity::class, ["id"], ["studentId"]), ForeignKey(SectionEntity::class, ["id"], ["sectionId"])])
data class EnrollmentEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val studentId: Long, val enrollmentNo: String, val year: String,
  val registeredYear: String? = null, val sectionId: Long, val previousSchool: String? = null, val status: String = "enrolled")

@DatabaseView("SELECT e.enrollmentNo AS enrollmentNo, s.fullName AS fullName, c.name AS className, sec.name AS sectionName, e.year AS year, e.status AS status " +
  "FROM Enrollment e JOIN Students s ON s.id = e.studentId JOIN Sections sec ON sec.id = e.sectionId JOIN Classes c ON c.id = sec.classId WHERE s.deletedAt IS NULL",
  viewName = "EnrollmentIndex")
data class EnrollmentIndexRow(val enrollmentNo: String, val fullName: String, val className: String, val sectionName: String, val year: String, val status: String)

@Entity(tableName = "Attendance", indices = [Index(value = ["staffId", "date"], unique = true)],
  foreignKeys = [ForeignKey(StaffEntity::class, ["id"], ["staffId"], onDelete = ForeignKey.CASCADE)])
data class AttendanceEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val staffId: Long, val date: String,
  val status: String, val checkIn: String? = null, val checkOut: String? = null, val reason: String? = null, val notes: String? = null)
@Entity(tableName = "StudentAttendance", indices = [Index(value = ["enrollmentId", "date"], unique = true)]) data class StudentAttendanceEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0, val enrollmentId: Long, val date: String, val status: String, val notes: String? = null)

@Entity(tableName = "Allowances", indices = [Index("nextDate")]) data class AllowanceEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0, val staffId: Long, val lastDate: String? = null, val nextDate: String? = null, val orderNo: String? = null, val notes: String? = null)
@Entity(tableName = "Promotions", indices = [Index("nextDate")]) data class PromotionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0, val staffId: Long, val fromGrade: Int? = null, val toGrade: Int? = null,
  val lastDate: String? = null, val nextDate: String? = null, val orderNo: String? = null, val notes: String? = null)
@Entity(tableName = "Transfers") data class TransferEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val studentId: Long, val enrollmentId: Long? = null,
  val fromSchool: String? = null, val toSchool: String? = null, val reason: String? = null, val date: String, val letterNo: String? = null, val notes: String? = null)

@Entity(tableName = "Grades", indices = [Index(value = ["enrollmentId", "subjectId"], unique = true)]) data class GradeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0, val enrollmentId: Long, val subjectId: Long, val month1: Double? = null, val month2: Double? = null,
  val midyear: Double? = null, val month3: Double? = null, val month4: Double? = null, val finalExam: Double? = null, val notes: String? = null)
@Entity(tableName = "PrimaryGrades", indices = [Index(value = ["enrollmentId", "subjectId", "term"], unique = true)]) data class PrimaryGradeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0, val enrollmentId: Long, val subjectId: Long, val term: String, val score: Double? = null, val evaluation: String? = null, val notes: String? = null)

@Entity(tableName = "Reports") data class ReportEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val type: String, val paramsJson: String? = null,
  val createdBy: Long? = null, val createdAt: String, val filePath: String? = null)
@Entity(tableName = "Notifications") data class NotificationEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val type: String, val message: String,
  val refTable: String? = null, val refId: Long? = null, val isRead: Boolean = false, val createdAt: String)
@Entity(tableName = "AuditLogs", indices = [Index("at")]) data class AuditLogEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val userId: Long? = null,
  val action: String, val tableName: String? = null, val recordId: Long? = null, val detailsJson: String? = null, val at: String)
@Entity(tableName = "Settings") data class SettingEntity(@PrimaryKey val key: String, val value: String)
@Entity(tableName = "Backups") data class BackupEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val path: String, val location: String,
  val encrypted: Boolean = true, val sizeBytes: Long? = null, val createdAt: String)

@Entity(tableName = "StudentDocuments", indices = [Index("studentId")]) data class DocumentEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val studentId: Long,
  val title: String, val path: String, val mime: String, val addedAt: String)
