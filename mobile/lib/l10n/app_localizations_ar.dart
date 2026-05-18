// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Arabic (`ar`).
class AppLocalizationsAr extends AppLocalizations {
  AppLocalizationsAr([String locale = 'ar']) : super(locale);

  @override
  String get app_title => 'زيلو';

  @override
  String get sign_in => 'تسجيل الدخول';

  @override
  String get sign_up => 'إنشاء حساب';

  @override
  String get email => 'البريد الإلكتروني';

  @override
  String get password => 'كلمة المرور';

  @override
  String get username => 'اسم المستخدم';

  @override
  String get required => 'مطلوب';

  @override
  String min_characters(int count) {
    return '$count أحرف على الأقل';
  }

  @override
  String get signing_in => 'جارٍ تسجيل الدخول...';

  @override
  String get dont_have_account => 'ليس لديك حساب؟ سجّل الآن';

  @override
  String get already_have_account => 'لديك حساب بالفعل؟ سجّل الدخول';

  @override
  String get creating_account => 'جارٍ الإنشاء...';

  @override
  String get create_account => 'إنشاء حساب';

  @override
  String get search => 'بحث';

  @override
  String get search_posts_placeholder => 'البحث في المقالات...';

  @override
  String get type_to_search => 'اكتب للبحث';

  @override
  String get xilo => 'زيلو';

  @override
  String failed_to_load(String item) {
    return 'فشل في تحميل $item. حاول مرة أخرى.';
  }

  @override
  String get no_posts_yet => 'لا توجد مقالات بعد';

  @override
  String get check_back_later => 'تحقق لاحقاً للحصول على محتوى جديد';

  @override
  String get premium => 'مميز';

  @override
  String get unknown => 'غير معروف';

  @override
  String min_read(int minutes) {
    return '$minutes دقيقة للقراءة';
  }

  @override
  String get just_now => 'الآن';

  @override
  String minutes_ago(int m) {
    return 'منذ $m دقيقة';
  }

  @override
  String hours_ago(int h) {
    return 'منذ $h ساعة';
  }

  @override
  String days_ago(int d) {
    return 'منذ $d يوم';
  }

  @override
  String get retry => 'إعادة المحاولة';

  @override
  String get failed_to_load_post => 'فشل في تحميل المقال';

  @override
  String get comments => 'التعليقات';

  @override
  String get no_comments_yet => 'لا توجد تعليقات بعد. كن أول من يعلق!';

  @override
  String get write => 'كتابة';

  @override
  String get post_title_hint => 'عنوان المقال';

  @override
  String get tell_story_hint => 'اكتب قصتك...';

  @override
  String get publish => 'نشر';

  @override
  String get notifications => 'الإشعارات';

  @override
  String get no_notifications_yet => 'لا توجد إشعارات بعد';

  @override
  String get bookmarks => 'المحفوظات';

  @override
  String get no_bookmarks_yet => 'لا توجد محفوظات بعد';

  @override
  String get settings => 'الإعدادات';

  @override
  String get profile => 'الملف الشخصي';

  @override
  String get billing_subscription => 'الفواتير والاشتراك';

  @override
  String get appearance => 'المظهر';

  @override
  String get language => 'اللغة';

  @override
  String get select_language => 'اختر اللغة';

  @override
  String get about => 'حول';

  @override
  String get follow => 'متابعة';

  @override
  String get unfollow => 'إلغاء المتابعة';

  @override
  String get display_name => 'الاسم المعروض';

  @override
  String get bio_placeholder => 'السيرة الذاتية هنا';

  @override
  String get nav_home => 'الرئيسية';

  @override
  String get nav_search => 'بحث';

  @override
  String get nav_write => 'كتابة';

  @override
  String get nav_notifications => 'الإشعارات';

  @override
  String get nav_profile => 'الملف الشخصي';

  @override
  String get image_not_supported => 'الصورة غير مدعومة';

  @override
  String get subscription_plans => 'خطط الاشتراك';

  @override
  String get manage => 'إدارة';

  @override
  String get active_subscription => 'اشتراك نشط';

  @override
  String get choose_plan => 'اختر خطة';

  @override
  String get free => 'مجاني';

  @override
  String get per_month => '/ شهرياً';

  @override
  String get per_year => '/ سنوياً';

  @override
  String get current_plan => 'الخطة الحالية';

  @override
  String get get_started_free => 'ابدأ مجاناً';

  @override
  String get subscribe => 'اشترك';

  @override
  String get payment_success => 'تم الدفع بنجاح! اشتراكك نشط الآن.';

  @override
  String get payment_failed => 'تم إلغاء الدفع أو فشل.';

  @override
  String get error_prefix => 'خطأ:';

  @override
  String get my_subscription => 'اشتراكي';

  @override
  String get cancel_subscription_title => 'إلغاء الاشتراك';

  @override
  String get cancel_subscription_msg =>
      'سيظل اشتراكك نشطاً حتى نهاية فترة الفوترة الحالية. هل أنت متأكد؟';

  @override
  String get no => 'لا';

  @override
  String get cancel_subscription_btn => 'إلغاء الاشتراك';

  @override
  String get subscription_cancelled => 'تم إلغاء الاشتراك.';

  @override
  String get cancel_failed => 'فشل في إلغاء الاشتراك.';

  @override
  String get active_plan => 'الخطة النشطة';

  @override
  String get expires => 'ينتهي';

  @override
  String get no_active_subscription => 'لا يوجد اشتراك نشط';

  @override
  String get choose_plan_get_started => 'اختر خطة للبدء';

  @override
  String get view_plans => 'عرض الخطط';

  @override
  String get billing_history => 'سجل الفواتير';

  @override
  String get no_invoices_yet => 'لا توجد فواتير بعد';

  @override
  String get paid => 'مدفوع';

  @override
  String get pending => 'قيد الانتظار';

  @override
  String get zarinpal_payment => 'دفع زرین‌بال';

  @override
  String get comments_title => 'التعليقات';

  @override
  String get send => 'إرسال';

  @override
  String get write_comment_hint => 'اكتب تعليقاً...';
}
