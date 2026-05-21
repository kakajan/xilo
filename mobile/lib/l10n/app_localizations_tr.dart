// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Turkish (`tr`).
class AppLocalizationsTr extends AppLocalizations {
  AppLocalizationsTr([String locale = 'tr']) : super(locale);

  @override
  String get app_title => 'Xilo';

  @override
  String get sign_in => 'Giriş Yap';

  @override
  String get sign_up => 'Kayıt Ol';

  @override
  String get email => 'E-posta';

  @override
  String get password => 'Şifre';

  @override
  String get username => 'Kullanıcı adı';

  @override
  String get required => 'Gerekli';

  @override
  String min_characters(int count) {
    return 'En az $count karakter';
  }

  @override
  String get signing_in => 'Giriş yapılıyor...';

  @override
  String get dont_have_account => 'Hesabınız yok mu? Kayıt olun';

  @override
  String get already_have_account => 'Zaten hesabınız var mı? Giriş yapın';

  @override
  String get creating_account => 'Oluşturuluyor...';

  @override
  String get create_account => 'Hesap oluştur';

  @override
  String get search => 'Ara';

  @override
  String get search_posts_placeholder => 'Yazılarda ara...';

  @override
  String get type_to_search => 'Aramak için yazın';

  @override
  String get xilo => 'Xilo';

  @override
  String failed_to_load(String item) {
    return '$item yüklenemedi. Tekrar deneyin.';
  }

  @override
  String get no_posts_yet => 'Henüz yazı yok';

  @override
  String get check_back_later => 'Yeni içerikler için tekrar gelin';

  @override
  String get premium => 'Premium';

  @override
  String get unknown => 'Bilinmiyor';

  @override
  String min_read(int minutes) {
    return '$minutes dk okuma';
  }

  @override
  String get just_now => 'Şimdi';

  @override
  String minutes_ago(int m) {
    return '$m dk önce';
  }

  @override
  String hours_ago(int h) {
    return '$h sa önce';
  }

  @override
  String days_ago(int d) {
    return '$d gün önce';
  }

  @override
  String get retry => 'Tekrar Dene';

  @override
  String get failed_to_load_post => 'Yazı yüklenemedi';

  @override
  String get comments => 'Yorumlar';

  @override
  String get no_comments_yet => 'Henüz yorum yok. İlk sen yaz!';

  @override
  String get write => 'Yaz';

  @override
  String get post_title_hint => 'Yazı başlığı';

  @override
  String get tell_story_hint => 'Hikayenizi anlatın...';

  @override
  String get publish => 'Yayınla';

  @override
  String get notifications => 'Bildirimler';

  @override
  String get no_notifications_yet => 'Bildirim yok';

  @override
  String get bookmarks => 'Yer imleri';

  @override
  String get no_bookmarks_yet => 'Yer imi yok';

  @override
  String get settings => 'Ayarlar';

  @override
  String get profile => 'Profil';

  @override
  String get billing_subscription => 'Faturalandırma ve Abonelik';

  @override
  String get appearance => 'Görünüm';

  @override
  String get language => 'Dil';

  @override
  String get select_language => 'Dil Seçin';

  @override
  String get about => 'Hakkında';

  @override
  String get follow => 'Takip Et';

  @override
  String get unfollow => 'Takibi Bırak';

  @override
  String get display_name => 'Görünen ad';

  @override
  String get bio_placeholder => 'Biyografi burada';

  @override
  String get nav_home => 'Ana Sayfa';

  @override
  String get nav_search => 'Ara';

  @override
  String get nav_write => 'Yaz';

  @override
  String get nav_notifications => 'Bildirimler';

  @override
  String get nav_profile => 'Profil';

  @override
  String get image_not_supported => 'Görsel desteklenmiyor';

  @override
  String get subscription_plans => 'Abonelik Planları';

  @override
  String get manage => 'Yönet';

  @override
  String get active_subscription => 'Aktif Abonelik';

  @override
  String get choose_plan => 'Bir Plan Seçin';

  @override
  String get free => 'Ücretsiz';

  @override
  String get per_month => '/ aylık';

  @override
  String get per_year => '/ yıllık';

  @override
  String get current_plan => 'Mevcut Plan';

  @override
  String get get_started_free => 'Ücretsiz Başla';

  @override
  String get subscribe => 'Abone Ol';

  @override
  String get payment_success => 'Ödeme başarılı! Aboneliğiniz şimdi aktif.';

  @override
  String get payment_failed => 'Ödeme iptal edildi veya başarısız oldu.';

  @override
  String get error_prefix => 'Hata:';

  @override
  String get my_subscription => 'Aboneliğim';

  @override
  String get cancel_subscription_title => 'Aboneliği İptal Et';

  @override
  String get cancel_subscription_msg =>
      'Aboneliğiniz mevcut fatura döneminin sonuna kadar aktif kalacaktır. Emin misiniz?';

  @override
  String get no => 'Hayır';

  @override
  String get cancel_subscription_btn => 'Aboneliği İptal Et';

  @override
  String get subscription_cancelled => 'Abonelik iptal edildi.';

  @override
  String get cancel_failed => 'Abonelik iptal edilemedi.';

  @override
  String get active_plan => 'Aktif Plan';

  @override
  String get expires => 'Bitiş';

  @override
  String get no_active_subscription => 'Aktif Abonelik Yok';

  @override
  String get choose_plan_get_started => 'Başlamak için bir plan seçin';

  @override
  String get view_plans => 'Planları Gör';

  @override
  String get billing_history => 'Fatura Geçmişi';

  @override
  String get no_invoices_yet => 'Henüz fatura yok';

  @override
  String get paid => 'Ödendi';

  @override
  String get pending => 'Beklemede';

  @override
  String get zarinpal_payment => 'Zarinpal Ödeme';

  @override
  String get comments_title => 'Yorumlar';

  @override
  String get send => 'Gönder';

  @override
  String get write_comment_hint => 'Yorum yaz...';

  @override
  String get tab_posts => 'Posts';

  @override
  String get tab_replies => 'Replies';

  @override
  String get tab_media => 'Media';

  @override
  String get tab_likes => 'Likes';

  @override
  String get tab_followers => 'Followers';

  @override
  String get tab_following => 'Following';

  @override
  String get message => 'Message';

  @override
  String get share_profile => 'Share';

  @override
  String get message_coming_soon => 'Messaging is coming soon';
}
