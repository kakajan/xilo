// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Persian (`fa`).
class AppLocalizationsFa extends AppLocalizations {
  AppLocalizationsFa([String locale = 'fa']) : super(locale);

  @override
  String get app_title => 'زیلو';

  @override
  String get sign_in => 'ورود';

  @override
  String get sign_up => 'ثبت‌نام';

  @override
  String get email => 'ایمیل';

  @override
  String get password => 'رمز عبور';

  @override
  String get username => 'نام کاربری';

  @override
  String get required => 'الزامی';

  @override
  String min_characters(int count) {
    return 'حداقل $count کاراکتر';
  }

  @override
  String get signing_in => 'در حال ورود...';

  @override
  String get dont_have_account => 'حساب کاربری ندارید؟ ثبت‌نام';

  @override
  String get already_have_account => 'حساب کاربری دارید؟ ورود';

  @override
  String get creating_account => 'در حال ایجاد...';

  @override
  String get create_account => 'ایجاد حساب';

  @override
  String get search => 'جستجو';

  @override
  String get search_posts_placeholder => 'جستجو در نوشته‌ها...';

  @override
  String get type_to_search => 'برای جستجو تایپ کنید';

  @override
  String get xilo => 'زیلو';

  @override
  String failed_to_load(String item) {
    return 'خطا در بارگذاری $item. دوباره تلاش کنید.';
  }

  @override
  String get no_posts_yet => 'هنوز نوشته‌ای وجود ندارد';

  @override
  String get check_back_later => 'بعداً برای محتوای جدید سر بزنید';

  @override
  String get premium => 'ویژه';

  @override
  String get unknown => 'نامشخص';

  @override
  String min_read(int minutes) {
    return '$minutes دقیقه مطالعه';
  }

  @override
  String get just_now => 'همین الان';

  @override
  String minutes_ago(int m) {
    return '$m دقیقه پیش';
  }

  @override
  String hours_ago(int h) {
    return '$h ساعت پیش';
  }

  @override
  String days_ago(int d) {
    return '$d روز پیش';
  }

  @override
  String get retry => 'تلاش مجدد';

  @override
  String get failed_to_load_post => 'خطا در بارگذاری نوشته';

  @override
  String get comments => 'نظرات';

  @override
  String get no_comments_yet => 'هنوز نظری نبوده، می‌خوای اولین نفر باشی؟';

  @override
  String get write => 'نوشتن';

  @override
  String get post_title_hint => 'عنوان نوشته';

  @override
  String get tell_story_hint => 'داستان خود را بنویسید...';

  @override
  String get publish => 'انتشار';

  @override
  String get notifications => 'اعلان‌ها';

  @override
  String get no_notifications_yet => 'هنوز اعلانی ندارید';

  @override
  String get bookmarks => 'نشان‌شده‌ها';

  @override
  String get no_bookmarks_yet => 'هنوز نشان‌شده‌ای ندارید';

  @override
  String get settings => 'تنظیمات';

  @override
  String get profile => 'پروفایل';

  @override
  String get billing_subscription => 'صورتحساب و اشتراک';

  @override
  String get appearance => 'ظاهر';

  @override
  String get language => 'زبان';

  @override
  String get select_language => 'انتخاب زبان';

  @override
  String get about => 'درباره';

  @override
  String get follow => 'دنبال کردن';

  @override
  String get unfollow => 'لغو دنبال کردن';

  @override
  String get display_name => 'نام نمایشی';

  @override
  String get bio_placeholder => 'بیوگرافی اینجا قرار می‌گیرد';

  @override
  String get nav_home => 'خانه';

  @override
  String get nav_search => 'جستجو';

  @override
  String get nav_write => 'نوشتن';

  @override
  String get nav_notifications => 'اعلان‌ها';

  @override
  String get nav_profile => 'پروفایل';

  @override
  String get image_not_supported => 'تصویر پشتیبانی نمی‌شود';

  @override
  String get subscription_plans => 'طرح‌های اشتراک';

  @override
  String get manage => 'مدیریت';

  @override
  String get active_subscription => 'اشتراک فعال';

  @override
  String get choose_plan => 'یک طرح انتخاب کنید';

  @override
  String get free => 'رایگان';

  @override
  String get per_month => '/ ماهانه';

  @override
  String get per_year => '/ سالانه';

  @override
  String get current_plan => 'طرح فعلی';

  @override
  String get get_started_free => 'شروع رایگان';

  @override
  String get subscribe => 'اشتراک';

  @override
  String get payment_success => 'پرداخت موفق! اشتراک شما فعال شد.';

  @override
  String get payment_failed => 'پرداخت لغو یا ناموفق بود.';

  @override
  String get error_prefix => 'خطا:';

  @override
  String get my_subscription => 'اشتراک من';

  @override
  String get cancel_subscription_title => 'لغو اشتراک';

  @override
  String get cancel_subscription_msg =>
      'اشتراک شما تا پایان دوره فعلی فعال می‌ماند. مطمئنید؟';

  @override
  String get no => 'خیر';

  @override
  String get cancel_subscription_btn => 'لغو اشتراک';

  @override
  String get subscription_cancelled => 'اشتراک لغو شد.';

  @override
  String get cancel_failed => 'خطا در لغو اشتراک.';

  @override
  String get active_plan => 'طرح فعال';

  @override
  String get expires => 'انقضا';

  @override
  String get no_active_subscription => 'اشتراک فعالی ندارید';

  @override
  String get choose_plan_get_started => 'برای شروع یک طرح انتخاب کنید';

  @override
  String get view_plans => 'مشاهده طرح‌ها';

  @override
  String get billing_history => 'تاریخچه صورتحساب';

  @override
  String get no_invoices_yet => 'هنوز فاکتوری وجود ندارد';

  @override
  String get paid => 'پرداخت‌شده';

  @override
  String get pending => 'در انتظار';

  @override
  String get zarinpal_payment => 'پرداخت زرین‌پال';

  @override
  String get comments_title => 'نظرات';

  @override
  String get send => 'ارسال';

  @override
  String get write_comment_hint => 'نظر خود را بنویسید...';

  @override
  String get tab_posts => 'پست‌ها';

  @override
  String get tab_replies => 'پاسخ‌ها';

  @override
  String get tab_media => 'رسانه';

  @override
  String get tab_likes => 'پسندیده‌ها';

  @override
  String get tab_followers => 'دنبال‌کنندگان';

  @override
  String get tab_following => 'دنبال‌شده‌ها';

  @override
  String get message => 'پیام';

  @override
  String get share_profile => 'اشتراک';

  @override
  String get message_coming_soon => 'پیام‌رسانی به‌زودی فعال می‌شود';
}
