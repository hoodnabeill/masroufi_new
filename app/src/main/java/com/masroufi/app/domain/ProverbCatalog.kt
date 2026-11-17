package com.masroufi.app.domain

/** The six headings of 14-PROVERBS.md; the set only picks the card colour. */
enum class ProverbSet { SAVING, SPENDING, DEBT, WORK, PEOPLE, PATIENCE }

data class Proverb(val number: Int, val text: String, val line: String, val set: ProverbSet, val rare: Boolean)

/** The 50 drafted cards of 14-PROVERBS.md, verbatim. Rare: 45 (first full week), 49 (first season), 50 (30 tickets in a row). */
object ProverbCatalog {
    val rare: Set<Int> = setOf(45, 49, 50)

    val all: List<Proverb> = listOf(
        card(1, "القرش الأبيض ينفع في اليوم الأسود", "اللي تحوّشه وانت مرتاح هو اللي يسندك وقت الزنقة.", ProverbSet.SAVING),
        card(2, "على قد لحافك مد رجليك", "اصرف على قد اللي معاك، مش على قد اللي نفسك فيه.", ProverbSet.SAVING),
        card(3, "دبّر غداك تلقى عشاك", "اللي يحسبها الصبح يلاقيها بالليل.", ProverbSet.SAVING),
        card(4, "التدبير نص المعيشة", "نص الشطارة إنك تعرف فلوسك رايحة فين.", ProverbSet.SAVING),
        card(5, "قليل دايم ولا كتير مقطوع", "خمسة جنيه كل يوم أحسن من ألف مرة واحدة.", ProverbSet.SAVING),
        card(6, "اللي يحسب الحسابات في الهنا يبات", "حسبتها النهارده؟ نام مرتاح.", ProverbSet.SAVING),
        card(7, "خد من التل يختل", "حتى الكتير بيخلص لو فضلت تسحب منه.", ProverbSet.SAVING),
        card(8, "اللي معهوش ما يلزموش", "لو مش قادر عليها دلوقتي، يبقى مش محتاجها دلوقتي.", ProverbSet.SAVING),
        card(9, "الطمع يقل ما جمع", "اللي عايز كل حاجة مرة واحدة بيخسر اللي في إيده.", ProverbSet.SAVING),
        card(10, "عصفور في الإيد ولا عشرة على الشجرة", "اللي في الحصّالة أضمن من اللي في الخيال.", ProverbSet.SAVING),
        card(11, "القناعة كنز لا يفنى", "الرضا باللي معاك أول الغنى.", ProverbSet.SAVING),
        card(12, "الباب اللي يجيلك منه الريح سدّه واستريح", "المصروف اللي بيخرّب عليك كل شهر، اقفله.", ProverbSet.SAVING),
        card(13, "الغالي تمنه فيه", "ساعات الأغلى هو الأوفر على المدى الطويل.", ProverbSet.SPENDING),
        card(14, "اللي ييجي بالساهل يروح بالساهل", "الفلوس اللي جت فجأة، احجز منها قبل ما تطير.", ProverbSet.SPENDING),
        card(15, "مال تجيبه الرياح تاخده الزوابع", "نفس المعنى، بس بالفصحى.", ProverbSet.SPENDING),
        card(16, "المال السايب يعلّم السرقة", "فلوس من غير حساب بتضيع من غير ما تحس.", ProverbSet.SPENDING),
        card(17, "اللي يعوزه البيت يحرم على الجامع", "الأساسيات الأول، وبعدين أي حاجة تانية.", ProverbSet.SPENDING),
        card(18, "العين بصيرة والإيد قصيرة", "عادي إن نفسك في حاجات أكتر من اللي تقدر عليه. اكتبها في «نِفسي فيه».", ProverbSet.SPENDING),
        card(19, "لقمة هنية تكفي مية", "مش لازم تصرف كتير عشان تتبسط.", ProverbSet.SPENDING),
        card(20, "بصلة المحب خروف", "القيمة مش في السعر.", ProverbSet.SPENDING),
        card(21, "اللي يتلسع من الشوربة ينفخ في الزبادي", "اتعلمت من غلطة الشهر اللي فات؟ برافو.", ProverbSet.SPENDING),
        card(22, "اللي أوله شرط آخره نور", "اتفقوا على الفلوس من الأول، ترتاحوا في الآخر.", ProverbSet.SPENDING),
        card(23, "هين قرشك ولا تهين نفسك", "في حاجات تستاهل تدفع فيها.", ProverbSet.SPENDING),
        card(24, "ادّي العيش لخبّازه ولو ياكل نصه", "الصنعة عند أصحابها أرخص من التجربة.", ProverbSet.SPENDING),
        card(25, "السلف تلف والرد خسارة", "سلّف على قد ما تقدر تنسى.", ProverbSet.DEBT),
        card(26, "الدَين همّ بالليل ومذلة بالنهار", "سدّد الصغير قبل ما يكبر.", ProverbSet.DEBT),
        card(27, "كما تدين تدان", "اللي بتعمله مع الناس بيرجعلك.", ProverbSet.DEBT),
        card(28, "التاجر لما يفلّس يدوّر في دفاتره القديمة", "افتح «الناس» وشوف مين ليك عنده.", ProverbSet.DEBT),
        card(29, "حرّص ولا تخوّنش", "سجّل السلفة، مش عشان مش واثق، عشان متنساش.", ProverbSet.DEBT),
        card(30, "الإيد البطالة نجسة", "الشغل، أي شغل، بركة.", ProverbSet.WORK),
        card(31, "اسعى يا عبد وأنا أسعى معاك", "اتحرك والرزق يتحرك.", ProverbSet.WORK),
        card(32, "الرزق يحب الخفّية", "اللي يصحى بدري يلحق.", ProverbSet.WORK),
        card(33, "من جدّ وجد", "التعب بييجي بنتيجته.", ProverbSet.WORK),
        card(34, "اللي يزرع يحصد", "اللي تحوّشه النهارده تلاقيه بكره.", ProverbSet.WORK),
        card(35, "قيراط حظ ولا فدان شطارة", "ساعات بتيجي بالحظ، بس متعتمدش عليه.", ProverbSet.WORK),
        card(36, "الشاطرة تغزل برجل حمار", "الشاطر بيدبّر بأي حاجة معاه.", ProverbSet.WORK),
        card(37, "بيت المهمل يخرب قبل بيت الظالم", "الإهمال في الفلوس أخطر من قلّتها.", ProverbSet.WORK),
        card(38, "اللي معاه قرش يساوي قرش", "الناس بتحترم اللي ماسك نفسه.", ProverbSet.PEOPLE),
        card(39, "الفلوس تغيّر النفوس", "خلّي بالك، وخلّيك إنت.", ProverbSet.PEOPLE),
        card(40, "الفلوس وسخ إيدين", "بتيجي وتروح. المهم إنت.", ProverbSet.PEOPLE),
        card(41, "الدراهم مراهم", "الفلوس مش كل حاجة، بس بتحل حاجات كتير.", ProverbSet.PEOPLE),
        card(42, "بفلوسك بنت السلطان عروسك", "مثل قديم بيضحّك: اللي معاه بيتدلّع.", ProverbSet.PEOPLE),
        card(43, "اللي يدفع القرش يزمّر ابنه", "اللي بيدفع هو اللي بيختار.", ProverbSet.PEOPLE),
        card(44, "اللي ياكل لوحده يزور", "الكرم على قد المقدرة حلو.", ProverbSet.PEOPLE),
        card(45, "اصرف ما في الجيب يأتيك ما في الغيب", "اتبسط شوية، بس خلّي الحصّالة شغّالة.", ProverbSet.PEOPLE),
        card(46, "الصبر مفتاح الفرج", "آخر الشهر بيعدّي. كل شهر بيعدّي.", ProverbSet.PATIENCE),
        card(47, "اللي يبص لفوق يتعب", "قارن نفسك بنفسك الأسبوع اللي فات، مش بغيرك.", ProverbSet.PATIENCE),
        card(48, "اللي ما يرضى بالخوخ يرضى بشرابه", "اللي يستنى الأحسن ساعات بيخسر الكويس.", ProverbSet.PATIENCE),
        card(49, "كل شيء بأوان", "رمضان والعيد والمدارس، كلهم ليهم ميعاد. جهّزلهم.", ProverbSet.PATIENCE),
        card(50, "خير الأمور الوسط", "لا بخل ولا تبذير.", ProverbSet.PATIENCE),
    )

    private fun card(number: Int, text: String, line: String, set: ProverbSet) =
        Proverb(number, text, line, set, rare = number in rare)
}
