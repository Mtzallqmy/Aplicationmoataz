package ai.alaser.app.ui.i18n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

@Composable
fun AlaserText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val configuration = LocalConfiguration.current
    val arabic = configuration.locales.get(0)?.language == "ar"
    androidx.compose.material3.Text(
        text = if (arabic) translate(text) else text,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        style = style,
    )
}

fun localizedContext(context: Context): Context {
    val language = context.getSharedPreferences("alaser_language", Context.MODE_PRIVATE)
        .getString("language", null)
        ?: return context
    val locale = Locale.forLanguageTag(language)
    Locale.setDefault(locale)
    val configuration = Configuration(context.resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return context.createConfigurationContext(configuration)
}

fun setApplicationLanguage(context: Context, language: String) {
    require(language in setOf("ar", "en")) { "Unsupported application language." }
    context.getSharedPreferences("alaser_language", Context.MODE_PRIVATE)
        .edit().putString("language", language).apply()
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) {
            current.recreate()
            return
        }
        current = current.baseContext
    }
}

private fun translate(value: String): String = translations[value]
    ?: translatedPrefixes.entries.firstOrNull { value.startsWith(it.key) }?.let {
        it.value + value.removePrefix(it.key)
    }
    ?: value

private val translatedPrefixes = linkedMapOf(
    "Workspace: " to "مساحة العمل: ",
    "Project: " to "المشروع: ",
    "Current project environment: " to "بيئة المشروع الحالية: ",
    "Interactive PTY active · " to "الطرفية التفاعلية نشطة · ",
    "Tools: " to "الأدوات: ",
    "HTTP " to "HTTP ",
)

private val translations = mapOf(
    "AI Providers" to "مزودو الذكاء الاصطناعي",
    "API key" to "مفتاح API",
    "About" to "حول التطبيق",
    "Open-source acknowledgements" to "شكر وإسناد المشاريع مفتوحة المصدر",
    "Architecture references" to "المراجع المعمارية",
    "Action could not be completed" to "تعذّر إكمال العملية",
    "Active for this project" to "نشطة لهذا المشروع",
    "Add" to "إضافة",
    "Add MCP server" to "إضافة خادم MCP",
    "Add an AI provider" to "إضافة مزود ذكاء اصطناعي",
    "Add another verified Linux distribution" to "إضافة توزيعة لينكس أخرى موثوقة",
    "Agent" to "الوكيل",
    "Allow once" to "سماح لمرة واحدة",
    "Allow this tool for session" to "السماح لهذه الأداة طوال الجلسة",
    "Allowed Telegram user IDs" to "معرّفات مستخدمي تيليجرام المسموح لهم",
    "Allowed chat IDs (optional)" to "معرّفات المحادثات المسموح بها، اختياري",
    "Alpine Linux installed" to "تم تثبيت Alpine Linux",
    "Approval required" to "الموافقة مطلوبة",
    "Arabic" to "العربية",
    "Base URL" to "الرابط الأساسي",
    "Bot name" to "اسم البوت",
    "Bot token" to "رمز البوت",
    "Branches" to "الفروع",
    "Browse files" to "تصفح الملفات",
    "Build from your phone" to "ابنِ مشاريعك من هاتفك",
    "Cancel" to "إلغاء",
    "Close" to "إغلاق",
    "Commit message" to "رسالة الحفظ في Git",
    "Create" to "إنشاء",
    "Create a project" to "إنشاء مشروع",
    "Create a project before browsing files." to "أنشئ مشروعًا أولاً لتصفح الملفات.",
    "Create a project to run shell commands." to "أنشئ مشروعًا لتشغيل أوامر الطرفية.",
    "Create commit" to "إنشاء Commit",
    "Create checkpoint" to "إنشاء نقطة استعادة",
    "Create folder instead" to "إنشاء مجلد بدلاً من ملف",
    "Delete" to "حذف",
    "Delete file or empty folder?" to "هل تريد حذف الملف أو المجلد الفارغ؟",
    "Deny" to "رفض",
    "Describe what you want to build…" to "اكتب ما تريد بناءه…",
    "Diff" to "التغييرات",
    "Dismiss" to "حسنًا",
    "Download and verify rootfs" to "تنزيل نظام لينكس والتحقق منه",
    "English" to "English",
    "Environment name" to "اسم البيئة",
    "Expected SHA-256 checksum" to "بصمة SHA-256 المتوقعة",
    "Git and Changes" to "Git والتغييرات",
    "Git commands run in your project's selected Android or Linux environment." to "تُنفذ أوامر Git داخل بيئة أندرويد أو لينكس المحددة للمشروع.",
    "HTTPS rootfs archive URL" to "رابط HTTPS لأرشيف نظام لينكس",
    "Import project ZIP from device" to "استيراد مشروع ZIP من الجهاز",
    "Initialize" to "تهيئة",
    "Inspect tools" to "عرض الأدوات",
    "Install Ubuntu with all development tools" to "تثبيت Ubuntu بجميع أدوات التطوير",
    "Install bundled Alpine Linux" to "تثبيت توزيعة Alpine المدمجة",
    "Installing Ubuntu…" to "جارٍ تثبيت Ubuntu…",
    "Installing Alpine…" to "جارٍ تثبيت Alpine…",
    "JSON-RPC HTTP endpoint" to "عنوان اتصال JSON-RPC HTTP",
    "Language" to "اللغة",
    "Linux Environments" to "بيئات لينكس",
    "Local workspace" to "مساحة عمل محلية",
    "Log" to "السجل",
    "MCP Servers" to "خوادم MCP",
    "MCP server name" to "اسم خادم MCP",
    "Model identifier" to "معرّف النموذج",
    "Native Android shell" to "طرفية أندرويد الأساسية",
    "New file path" to "مسار الملف الجديد",
    "New session" to "جلسة جديدة",
    "New workspace-relative path" to "المسار الجديد داخل مساحة العمل",
    "No model configured" to "لم يتم إعداد نموذج بعد",
    "Parent" to "المجلد السابق",
    "Privacy" to "الخصوصية",
    "Project name" to "اسم المشروع",
    "Project checkpoints" to "نقاط استعادة المشروع",
    "Projects" to "المشاريع",
    "Provider name" to "اسم المزود",
    "Pull" to "سحب",
    "Push" to "رفع",
    "Push commits to the remote repository?" to "هل تريد رفع التعديلات إلى المستودع البعيد؟",
    "Remove" to "إزالة",
    "Restore" to "استعادة",
    "Restore project checkpoint?" to "هل تريد استعادة نقطة حفظ المشروع؟",
    "Files will be restored to the selected saved state." to "ستُستعاد الملفات إلى الحالة المحفوظة المحددة.",
    "Rename" to "إعادة تسمية",
    "Rename or move" to "إعادة تسمية أو نقل",
    "Required; separate multiple IDs with commas." to "مطلوب؛ افصل المعرّفات المتعددة بفاصلة.",
    "Run" to "تشغيل",
    "Save" to "حفظ",
    "Save encrypted bot" to "حفظ البوت مشفرًا",
    "Save encrypted provider" to "حفظ المزود مشفرًا",
    "Selected for new agent tasks" to "محدد لمهام الوكيل الجديدة",
    "Select a project first." to "اختر مشروعًا أولاً.",
    "Settings" to "الإعدادات",
    "Shell command" to "أمر الطرفية",
    "Stage all" to "تجهيز جميع التغييرات",
    "Start interactive PTY" to "تشغيل الطرفية التفاعلية",
    "Status" to "الحالة",
    "Status, diff, history, branches, commits, pull, and confirmed push" to "الحالة والتغييرات والسجل والفروع والحفظ والسحب والرفع المؤكد",
    "Stop PTY" to "إيقاف الطرفية",
    "Telegram Bots" to "بوتات تيليجرام",
    "Terminal" to "الطرفية",
    "Test connection" to "اختبار الاتصال",
    "This publishes your committed changes to the configured Git remote." to "سيؤدي هذا إلى نشر تعديلاتك المحفوظة على مستودع Git البعيد.",
    "Ubuntu Developer installed" to "تم تثبيت Ubuntu Developer",
    "Use in terminal and agent" to "استخدامها في الطرفية والوكيل",
    "Use native Android shell" to "استخدام طرفية أندرويد الأساسية",
    "Verified local root filesystem" to "نظام ملفات لينكس محلي موثوق",
    "Workspace Files" to "ملفات مساحة العمل",
    "You" to "أنت",
    "Tool result" to "نتيجة الأداة",
    "System" to "النظام",
)
