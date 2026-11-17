package com.masroufi.app.domain

/** Grams of gold in the nisab (decision 7). */
const val NISAB_GOLD_GRAMS = 85L

/** Zakat al-mal: 2.5% of the zakatable money the user confirms, floored to piastres; 0 below the nisab. */
fun zakatMal(zakatable: Long, nisab: Long): Long {
    require(zakatable >= 0 && nisab >= 0) { "amounts must be >= 0" }
    return if (zakatable >= nisab) zakatable * 25 / 1000 else 0L
}

/** The nisab from the user's gold price per gram: 85 g. */
fun nisabFromGold(pricePerGram: Long): Long {
    require(pricePerGram > 0) { "gold price must be > 0" }
    return NISAB_GOLD_GRAMS * pricePerGram
}

/** Zakat al-fitr: people x the amount per person the user types. */
fun zakatFitr(people: Int, perPerson: Long): Long {
    require(people >= 0 && perPerson >= 0) { "people and amount must be >= 0" }
    return people * perPerson
}
