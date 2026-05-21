// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Russian (`ru`).
class AppLocalizationsRu extends AppLocalizations {
  AppLocalizationsRu([String locale = 'ru']) : super(locale);

  @override
  String get app_title => 'Xilo';

  @override
  String get sign_in => 'Войти';

  @override
  String get sign_up => 'Регистрация';

  @override
  String get email => 'Электронная почта';

  @override
  String get password => 'Пароль';

  @override
  String get username => 'Имя пользователя';

  @override
  String get required => 'Обязательно';

  @override
  String min_characters(int count) {
    return 'Минимум $count символов';
  }

  @override
  String get signing_in => 'Вход...';

  @override
  String get dont_have_account => 'Нет аккаунта? Зарегистрируйтесь';

  @override
  String get already_have_account => 'Уже есть аккаунт? Войдите';

  @override
  String get creating_account => 'Создание...';

  @override
  String get create_account => 'Создать аккаунт';

  @override
  String get search => 'Поиск';

  @override
  String get search_posts_placeholder => 'Поиск статей...';

  @override
  String get type_to_search => 'Введите для поиска';

  @override
  String get xilo => 'Xilo';

  @override
  String failed_to_load(String item) {
    return 'Не удалось загрузить $item. Попробуйте снова.';
  }

  @override
  String get no_posts_yet => 'Статей пока нет';

  @override
  String get check_back_later => 'Загляните позже за новым контентом';

  @override
  String get premium => 'Премиум';

  @override
  String get unknown => 'Неизвестно';

  @override
  String min_read(int minutes) {
    return '$minutes мин. чтения';
  }

  @override
  String get just_now => 'Только что';

  @override
  String minutes_ago(int m) {
    return '$m мин. назад';
  }

  @override
  String hours_ago(int h) {
    return '$h ч. назад';
  }

  @override
  String days_ago(int d) {
    return '$d дн. назад';
  }

  @override
  String get retry => 'Повторить';

  @override
  String get failed_to_load_post => 'Не удалось загрузить статью';

  @override
  String get comments => 'Комментарии';

  @override
  String get no_comments_yet => 'Комментариев пока нет. Будьте первым!';

  @override
  String get write => 'Написать';

  @override
  String get post_title_hint => 'Заголовок статьи';

  @override
  String get tell_story_hint => 'Расскажите свою историю...';

  @override
  String get publish => 'Опубликовать';

  @override
  String get notifications => 'Уведомления';

  @override
  String get no_notifications_yet => 'Уведомлений пока нет';

  @override
  String get bookmarks => 'Закладки';

  @override
  String get no_bookmarks_yet => 'Закладок пока нет';

  @override
  String get settings => 'Настройки';

  @override
  String get profile => 'Профиль';

  @override
  String get billing_subscription => 'Оплата и подписка';

  @override
  String get appearance => 'Оформление';

  @override
  String get language => 'Язык';

  @override
  String get select_language => 'Выберите язык';

  @override
  String get about => 'О приложении';

  @override
  String get follow => 'Подписаться';

  @override
  String get unfollow => 'Отписаться';

  @override
  String get display_name => 'Отображаемое имя';

  @override
  String get bio_placeholder => 'Биография';

  @override
  String get nav_home => 'Главная';

  @override
  String get nav_search => 'Поиск';

  @override
  String get nav_write => 'Написать';

  @override
  String get nav_notifications => 'Уведомления';

  @override
  String get nav_profile => 'Профиль';

  @override
  String get image_not_supported => 'Изображение не поддерживается';

  @override
  String get subscription_plans => 'Тарифные планы';

  @override
  String get manage => 'Управление';

  @override
  String get active_subscription => 'Активная подписка';

  @override
  String get choose_plan => 'Выберите тариф';

  @override
  String get free => 'Бесплатно';

  @override
  String get per_month => '/ мес.';

  @override
  String get per_year => '/ год';

  @override
  String get current_plan => 'Текущий тариф';

  @override
  String get get_started_free => 'Начать бесплатно';

  @override
  String get subscribe => 'Подписаться';

  @override
  String get payment_success => 'Оплата прошла успешно! Подписка активна.';

  @override
  String get payment_failed => 'Оплата отменена или не удалась.';

  @override
  String get error_prefix => 'Ошибка:';

  @override
  String get my_subscription => 'Моя подписка';

  @override
  String get cancel_subscription_title => 'Отменить подписку';

  @override
  String get cancel_subscription_msg =>
      'Подписка останется активной до конца текущего расчётного периода. Вы уверены?';

  @override
  String get no => 'Нет';

  @override
  String get cancel_subscription_btn => 'Отменить подписку';

  @override
  String get subscription_cancelled => 'Подписка отменена.';

  @override
  String get cancel_failed => 'Не удалось отменить подписку.';

  @override
  String get active_plan => 'Активный тариф';

  @override
  String get expires => 'Истекает';

  @override
  String get no_active_subscription => 'Нет активной подписки';

  @override
  String get choose_plan_get_started => 'Выберите тариф для начала';

  @override
  String get view_plans => 'Смотреть тарифы';

  @override
  String get billing_history => 'История платежей';

  @override
  String get no_invoices_yet => 'Счетов пока нет';

  @override
  String get paid => 'Оплачен';

  @override
  String get pending => 'В ожидании';

  @override
  String get zarinpal_payment => 'Оплата Zarinpal';

  @override
  String get comments_title => 'Комментарии';

  @override
  String get send => 'Отправить';

  @override
  String get write_comment_hint => 'Написать комментарий...';

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
