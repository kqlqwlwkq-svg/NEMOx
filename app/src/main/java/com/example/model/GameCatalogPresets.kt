package com.example.model

data class GameAccountSupportSpec(
    val gameKey: String,
    val gameName: String,
    val defaultPlatform: String,
    val apiMode: AccountApiMode,
    val identifierHintEn: String,
    val identifierHintAr: String,
    val availabilitySummaryEn: String,
    val availabilitySummaryAr: String,
    val officialSteamAppId: Long? = null,
    val officialUrl: String,
    val communityUrl: String
)

object GameCatalogPresets {
    val supportedAccountGames = listOf(
        GameAccountSupportSpec(
            gameKey = "minecraft",
            gameName = "Minecraft",
            defaultPlatform = "PC / Crossplay",
            apiMode = AccountApiMode.LIVE_OFFICIAL_API,
            identifierHintEn = "Minecraft Java Username (e.g. Notch)",
            identifierHintAr = "اسم مستخدم Minecraft Java",
            availabilitySummaryEn = "Official Mojang Public API: Verifies username, UUID, and live skin avatar. Server-specific levels/ranks are tracked manually.",
            availabilitySummaryAr = "API رسمي من Mojang: يجلب المعرف الفريد UUID وصورة الشخصية الحية. المستويات والرتب داخل السيرفرات تُسجل يدوياً.",
            officialUrl = "https://www.minecraft.net",
            communityUrl = "https://feedback.minecraft.net"
        ),
        GameAccountSupportSpec(
            gameKey = "steam",
            gameName = "Steam Profile",
            defaultPlatform = "PC",
            apiMode = AccountApiMode.LIVE_COMMUNITY_XML,
            identifierHintEn = "Steam Vanity ID or SteamID64",
            identifierHintAr = "معرف Steam المخصص أو SteamID64",
            availabilitySummaryEn = "Live Steam Public XML: Fetches avatar, persona name, online status, and recent playtime if your Steam profile is public.",
            availabilitySummaryAr = "بيانات Steam العامة المباشرة: تجلب الصورة واسم اللاعب والحالة وساعات اللعب الأخيرة إذا كان ملفك عاماً.",
            officialUrl = "https://store.steampowered.com",
            communityUrl = "https://steamcommunity.com"
        ),
        GameAccountSupportSpec(
            gameKey = "pubg_mobile",
            gameName = "PUBG Mobile",
            defaultPlatform = "Mobile",
            apiMode = AccountApiMode.MANUAL_NO_PUBLIC_API,
            identifierHintEn = "Numeric Player ID (e.g. 5123456789)",
            identifierHintAr = "معرف اللاعب الرقمي (Player ID)",
            availabilitySummaryEn = "No official public API is provided by Krafton/Tencent for PUBG Mobile player stats. Save your Player ID, tier, and level locally without any password.",
            availabilitySummaryAr = "لا توفر Krafton/Tencent واجهة API عامة رسمية لإحصائيات PUBG Mobile. يمكنك حفظ معرفك ومستواك ورتبتك يدوياً بأمان دون كلمة مرور.",
            officialSteamAppId = 578080L,
            officialUrl = "https://www.pubgmobile.com",
            communityUrl = "https://www.reddit.com/r/PUBGMobile"
        ),
        GameAccountSupportSpec(
            gameKey = "blood_strike",
            gameName = "Blood Strike",
            defaultPlatform = "Mobile / PC",
            apiMode = AccountApiMode.MANUAL_NO_PUBLIC_API,
            identifierHintEn = "Blood Strike Player ID",
            identifierHintAr = "معرف اللاعب في Blood Strike",
            availabilitySummaryEn = "NetEase does not expose a public official stats API for Blood Strike. Track your Player ID, rank, and level manually without passwords.",
            availabilitySummaryAr = "لا توفر NetEase واجهة API عامة رسمية لـ Blood Strike. يمكنك متابعة معرفك ورتبتك ومستواك يدوياً دون كلمات مرور.",
            officialSteamAppId = 3199170L,
            officialUrl = "https://www.blood-strike.com",
            communityUrl = "https://discord.com/invite/bloodstrike"
        ),
        GameAccountSupportSpec(
            gameKey = "fortnite",
            gameName = "Fortnite",
            defaultPlatform = "Crossplay",
            apiMode = AccountApiMode.MANUAL_NO_PUBLIC_API,
            identifierHintEn = "Epic Display Name",
            identifierHintAr = "اسم العرض في Epic Games",
            availabilitySummaryEn = "Live official Fortnite News feed is connected; however, personal player stats require private Epic OAuth/third-party keys. Track your Epic ID and Battle Pass level safely here.",
            availabilitySummaryAr = "أخبار Fortnite الحية متصلة مباشرة، بينما إحصائيات اللاعب الخاصة تتطلب مفاتيح Epic أو خدمات خارجية. سجّل اسمك ومستواك هنا بأمان.",
            officialUrl = "https://www.fortnite.com",
            communityUrl = "https://www.fortnite.com/news"
        ),
        GameAccountSupportSpec(
            gameKey = "cod",
            gameName = "Call of Duty",
            defaultPlatform = "PC / Console / Mobile",
            apiMode = AccountApiMode.MANUAL_NO_PUBLIC_API,
            identifierHintEn = "Activision ID (e.g. Player#1234567)",
            identifierHintAr = "معرف Activision (مثال: Player#1234)",
            availabilitySummaryEn = "Activision restricted public unauthenticated Call of Duty stats endpoints. Live patch news is pulled from Steam, while your Rank & Level are tracked manually.",
            availabilitySummaryAr = "قامت Activision بتقييد واجهات الإحصائيات العامة. نجلب آخر الأخبار والتحديثات مباشرة بينما تتبع الرتبة والمستوى يدوياً.",
            officialSteamAppId = 1938090L,
            officialUrl = "https://www.callofduty.com",
            communityUrl = "https://www.callofduty.com/blog"
        ),
        GameAccountSupportSpec(
            gameKey = "rocket_league",
            gameName = "Rocket League",
            defaultPlatform = "PC / Console",
            apiMode = AccountApiMode.MANUAL_NO_PUBLIC_API,
            identifierHintEn = "Epic / Platform Tag",
            identifierHintAr = "معرف Epic أو المنصة",
            availabilitySummaryEn = "Psyonix closed public API key registration for Rocket League stats. Live news is fetched directly while competitive ranks are tracked manually.",
            availabilitySummaryAr = "أغلقت Psyonix التسجيل العام لـ API إحصائيات Rocket League. الأخبار متصلة مباشرة ويمكنك تسجيل رتبتك التنافسية يدوياً.",
            officialSteamAppId = 252950L,
            officialUrl = "https://www.rocketleague.com",
            communityUrl = "https://www.rocketleague.com/news"
        ),
        GameAccountSupportSpec(
            gameKey = "custom",
            gameName = "Other Game",
            defaultPlatform = "Crossplay",
            apiMode = AccountApiMode.MANUAL_NO_PUBLIC_API,
            identifierHintEn = "Player ID or Username",
            identifierHintAr = "معرف اللاعب أو اسم المستخدم",
            availabilitySummaryEn = "Track any game account ID, level, rank, and achievements manually with zero passwords stored.",
            availabilitySummaryAr = "سجّل أي حساب لعبة ومستواه ورتبته وإنجازاته يدوياً دون تخزين أي كلمات مرور.",
            officialUrl = "",
            communityUrl = ""
        )
    )

    // Curated real Steam App IDs for default news feeds when user's library is empty
    val defaultNewsSteamAppIds: List<Pair<Long, String>> = listOf(
        578080L to "PUBG: BATTLEGROUNDS",
        1938090L to "Call of Duty",
        252950L to "Rocket League",
        730L to "Counter-Strike 2",
        1172470L to "Apex Legends",
        2767030L to "Marvel Rivals"
    )
}
