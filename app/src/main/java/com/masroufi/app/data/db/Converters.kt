package com.masroufi.app.data.db

import androidx.room.TypeConverter
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.StampKind
import com.masroufi.app.domain.TicketStatus
import java.time.LocalDate

/** LocalDate as epoch day; every enum by name, so a rename shows up here and in a migration. */
class Converters {
    @TypeConverter fun dateToLong(v: LocalDate?): Long? = v?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)

    @TypeConverter fun entryKindToString(v: EntryKind): String = v.name
    @TypeConverter fun stringToEntryKind(v: String): EntryKind = EntryKind.valueOf(v)
    @TypeConverter fun ticketStatusToString(v: TicketStatus): String = v.name
    @TypeConverter fun stringToTicketStatus(v: String): TicketStatus = TicketStatus.valueOf(v)
    @TypeConverter fun destinationToString(v: LeftoverDestination?): String? = v?.name
    @TypeConverter fun stringToDestination(v: String?): LeftoverDestination? = v?.let(LeftoverDestination::valueOf)
    @TypeConverter fun stampKindToString(v: StampKind): String = v.name
    @TypeConverter fun stringToStampKind(v: String): StampKind = StampKind.valueOf(v)
    @TypeConverter fun pocketKindToString(v: PocketKind): String = v.name
    @TypeConverter fun stringToPocketKind(v: String): PocketKind = PocketKind.valueOf(v)
    @TypeConverter fun envelopeKindToString(v: EnvelopeKind): String = v.name
    @TypeConverter fun stringToEnvelopeKind(v: String): EnvelopeKind = EnvelopeKind.valueOf(v)
    @TypeConverter fun recurringKindToString(v: RecurringKind): String = v.name
    @TypeConverter fun stringToRecurringKind(v: String): RecurringKind = RecurringKind.valueOf(v)
    @TypeConverter fun potToString(v: Pot): String = v.name
    @TypeConverter fun stringToPot(v: String): Pot = Pot.valueOf(v)
    @TypeConverter fun jarSourceToString(v: JarSource): String = v.name
    @TypeConverter fun stringToJarSource(v: String): JarSource = JarSource.valueOf(v)
}
