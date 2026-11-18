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
    @TypeConverter fun scheduleTypeToString(v: ScheduleType): String = v.name
    @TypeConverter fun stringToScheduleType(v: String): ScheduleType = ScheduleType.valueOf(v)
    @TypeConverter fun seasonKindToString(v: SeasonKind): String = v.name
    @TypeConverter fun stringToSeasonKind(v: String): SeasonKind = SeasonKind.valueOf(v)
    @TypeConverter fun splitKindToString(v: SplitKind): String = v.name
    @TypeConverter fun stringToSplitKind(v: String): SplitKind = SplitKind.valueOf(v)
    @TypeConverter fun personKindToString(v: PersonKind): String = v.name
    @TypeConverter fun stringToPersonKind(v: String): PersonKind = PersonKind.valueOf(v)
    @TypeConverter fun debtDirectionToString(v: DebtDirection): String = v.name
    @TypeConverter fun stringToDebtDirection(v: String): DebtDirection = DebtDirection.valueOf(v)
    @TypeConverter fun occasionOwnerToString(v: OccasionOwner): String = v.name
    @TypeConverter fun stringToOccasionOwner(v: String): OccasionOwner = OccasionOwner.valueOf(v)
    @TypeConverter fun giftDirectionToString(v: GiftDirection): String = v.name
    @TypeConverter fun stringToGiftDirection(v: String): GiftDirection = GiftDirection.valueOf(v)
    @TypeConverter fun wishAnswerToString(v: WishAnswer): String = v.name
    @TypeConverter fun stringToWishAnswer(v: String): WishAnswer = WishAnswer.valueOf(v)
    @TypeConverter fun advanceAnswerToString(v: AdvanceAnswer): String = v.name
    @TypeConverter fun stringToAdvanceAnswer(v: String): AdvanceAnswer = AdvanceAnswer.valueOf(v)
    @TypeConverter fun childJarKindToString(v: ChildJarKind): String = v.name
    @TypeConverter fun stringToChildJarKind(v: String): ChildJarKind = ChildJarKind.valueOf(v)
}
