package app.papertrail.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import app.papertrail.domain.Bill
import app.papertrail.domain.Case
import app.papertrail.domain.Clue
import app.papertrail.domain.Ghost
import app.papertrail.domain.Suspect
import app.papertrail.domain.Ticket

@Dao
interface TrailDao {
    @Query("SELECT * FROM suspect") suspend fun suspects(): List<Suspect>
    @Query("SELECT * FROM clue ORDER BY at") suspend fun clues(): List<Clue>
    @Query("SELECT * FROM affair ORDER BY id") suspend fun cases(): List<Case>
    @Query("SELECT * FROM ghost ORDER BY id") suspend fun ghosts(): List<Ghost>
    @Query("SELECT * FROM bill ORDER BY day") suspend fun bills(): List<Bill>
    @Query("SELECT * FROM ticket ORDER BY id") suspend fun tickets(): List<Ticket>

    @Insert suspend fun insert(s: Suspect): Long
    @Insert suspend fun insertSuspects(s: List<Suspect>)
    @Update suspend fun update(s: Suspect)

    @Insert suspend fun insert(c: Clue): Long
    @Insert suspend fun insertClues(c: List<Clue>)
    @Update suspend fun update(c: Clue)
    @Delete suspend fun delete(c: Clue)
    @Query("SELECT COUNT(*) FROM clue WHERE pkg = :pkg AND amount = :amount AND at > :since") suspend fun recent(pkg: String, amount: Long, since: Long): Int

    @Insert suspend fun insertCases(c: List<Case>)
    @Update suspend fun update(c: Case)

    @Insert suspend fun insertGhosts(g: List<Ghost>)
    @Update suspend fun update(g: Ghost)

    @Insert suspend fun insert(b: Bill): Long
    @Update suspend fun update(b: Bill)
    @Delete suspend fun delete(b: Bill)

    @Insert suspend fun insert(t: Ticket): Long
    @Update suspend fun update(t: Ticket)
    @Delete suspend fun delete(t: Ticket)
}

@Database(entities = [Suspect::class, Clue::class, Case::class, Ghost::class, Bill::class, Ticket::class], version = 1, exportSchema = true)
abstract class TrailDb : RoomDatabase() {
    abstract fun dao(): TrailDao

    companion object {
        fun create(c: Context): TrailDb = Room.databaseBuilder(c, TrailDb::class.java, "papertrail.db").build()
        fun memory(c: Context): TrailDb = Room.inMemoryDatabaseBuilder(c, TrailDb::class.java).build()
    }
}
