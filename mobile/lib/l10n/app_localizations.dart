import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_ar.dart';
import 'app_localizations_en.dart';
import 'app_localizations_fa.dart';
import 'app_localizations_ru.dart';
import 'app_localizations_tr.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('ar'),
    Locale('en'),
    Locale('fa'),
    Locale('ru'),
    Locale('tr'),
  ];

  /// No description provided for @app_title.
  ///
  /// In en, this message translates to:
  /// **'Xilo'**
  String get app_title;

  /// No description provided for @sign_in.
  ///
  /// In en, this message translates to:
  /// **'Sign In'**
  String get sign_in;

  /// No description provided for @sign_up.
  ///
  /// In en, this message translates to:
  /// **'Sign Up'**
  String get sign_up;

  /// No description provided for @email.
  ///
  /// In en, this message translates to:
  /// **'Email'**
  String get email;

  /// No description provided for @password.
  ///
  /// In en, this message translates to:
  /// **'Password'**
  String get password;

  /// No description provided for @username.
  ///
  /// In en, this message translates to:
  /// **'Username'**
  String get username;

  /// No description provided for @required.
  ///
  /// In en, this message translates to:
  /// **'Required'**
  String get required;

  /// No description provided for @min_characters.
  ///
  /// In en, this message translates to:
  /// **'Min {count} characters'**
  String min_characters(int count);

  /// No description provided for @signing_in.
  ///
  /// In en, this message translates to:
  /// **'Signing in...'**
  String get signing_in;

  /// No description provided for @dont_have_account.
  ///
  /// In en, this message translates to:
  /// **'Don\'t have an account? Sign Up'**
  String get dont_have_account;

  /// No description provided for @already_have_account.
  ///
  /// In en, this message translates to:
  /// **'Already have an account? Sign In'**
  String get already_have_account;

  /// No description provided for @creating_account.
  ///
  /// In en, this message translates to:
  /// **'Creating...'**
  String get creating_account;

  /// No description provided for @create_account.
  ///
  /// In en, this message translates to:
  /// **'Create Account'**
  String get create_account;

  /// No description provided for @search.
  ///
  /// In en, this message translates to:
  /// **'Search'**
  String get search;

  /// No description provided for @search_posts_placeholder.
  ///
  /// In en, this message translates to:
  /// **'Search posts...'**
  String get search_posts_placeholder;

  /// No description provided for @type_to_search.
  ///
  /// In en, this message translates to:
  /// **'Type to search'**
  String get type_to_search;

  /// No description provided for @xilo.
  ///
  /// In en, this message translates to:
  /// **'Xilo'**
  String get xilo;

  /// No description provided for @failed_to_load.
  ///
  /// In en, this message translates to:
  /// **'Failed to load {item}. Please try again.'**
  String failed_to_load(String item);

  /// No description provided for @no_posts_yet.
  ///
  /// In en, this message translates to:
  /// **'No posts yet'**
  String get no_posts_yet;

  /// No description provided for @check_back_later.
  ///
  /// In en, this message translates to:
  /// **'Check back later for new content'**
  String get check_back_later;

  /// No description provided for @premium.
  ///
  /// In en, this message translates to:
  /// **'Premium'**
  String get premium;

  /// No description provided for @unknown.
  ///
  /// In en, this message translates to:
  /// **'Unknown'**
  String get unknown;

  /// No description provided for @min_read.
  ///
  /// In en, this message translates to:
  /// **'{minutes} min read'**
  String min_read(int minutes);

  /// No description provided for @just_now.
  ///
  /// In en, this message translates to:
  /// **'Just now'**
  String get just_now;

  /// No description provided for @minutes_ago.
  ///
  /// In en, this message translates to:
  /// **'{m}m ago'**
  String minutes_ago(int m);

  /// No description provided for @hours_ago.
  ///
  /// In en, this message translates to:
  /// **'{h}h ago'**
  String hours_ago(int h);

  /// No description provided for @days_ago.
  ///
  /// In en, this message translates to:
  /// **'{d}d ago'**
  String days_ago(int d);

  /// No description provided for @retry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retry;

  /// No description provided for @failed_to_load_post.
  ///
  /// In en, this message translates to:
  /// **'Failed to load post'**
  String get failed_to_load_post;

  /// No description provided for @comments.
  ///
  /// In en, this message translates to:
  /// **'Comments'**
  String get comments;

  /// No description provided for @no_comments_yet.
  ///
  /// In en, this message translates to:
  /// **'No comments yet. Be the first!'**
  String get no_comments_yet;

  /// No description provided for @write.
  ///
  /// In en, this message translates to:
  /// **'Write'**
  String get write;

  /// No description provided for @post_title_hint.
  ///
  /// In en, this message translates to:
  /// **'Post title'**
  String get post_title_hint;

  /// No description provided for @tell_story_hint.
  ///
  /// In en, this message translates to:
  /// **'Tell your story...'**
  String get tell_story_hint;

  /// No description provided for @publish.
  ///
  /// In en, this message translates to:
  /// **'Publish'**
  String get publish;

  /// No description provided for @notifications.
  ///
  /// In en, this message translates to:
  /// **'Notifications'**
  String get notifications;

  /// No description provided for @no_notifications_yet.
  ///
  /// In en, this message translates to:
  /// **'No notifications yet'**
  String get no_notifications_yet;

  /// No description provided for @bookmarks.
  ///
  /// In en, this message translates to:
  /// **'Bookmarks'**
  String get bookmarks;

  /// No description provided for @no_bookmarks_yet.
  ///
  /// In en, this message translates to:
  /// **'No bookmarks yet'**
  String get no_bookmarks_yet;

  /// No description provided for @settings.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settings;

  /// No description provided for @profile.
  ///
  /// In en, this message translates to:
  /// **'Profile'**
  String get profile;

  /// No description provided for @billing_subscription.
  ///
  /// In en, this message translates to:
  /// **'Billing & Subscription'**
  String get billing_subscription;

  /// No description provided for @appearance.
  ///
  /// In en, this message translates to:
  /// **'Appearance'**
  String get appearance;

  /// No description provided for @language.
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get language;

  /// No description provided for @select_language.
  ///
  /// In en, this message translates to:
  /// **'Select Language'**
  String get select_language;

  /// No description provided for @about.
  ///
  /// In en, this message translates to:
  /// **'About'**
  String get about;

  /// No description provided for @follow.
  ///
  /// In en, this message translates to:
  /// **'Follow'**
  String get follow;

  /// No description provided for @unfollow.
  ///
  /// In en, this message translates to:
  /// **'Unfollow'**
  String get unfollow;

  /// No description provided for @display_name.
  ///
  /// In en, this message translates to:
  /// **'Display Name'**
  String get display_name;

  /// No description provided for @bio_placeholder.
  ///
  /// In en, this message translates to:
  /// **'Bio goes here'**
  String get bio_placeholder;

  /// No description provided for @nav_home.
  ///
  /// In en, this message translates to:
  /// **'Home'**
  String get nav_home;

  /// No description provided for @nav_search.
  ///
  /// In en, this message translates to:
  /// **'Search'**
  String get nav_search;

  /// No description provided for @nav_write.
  ///
  /// In en, this message translates to:
  /// **'Write'**
  String get nav_write;

  /// No description provided for @nav_notifications.
  ///
  /// In en, this message translates to:
  /// **'Notifications'**
  String get nav_notifications;

  /// No description provided for @nav_profile.
  ///
  /// In en, this message translates to:
  /// **'Profile'**
  String get nav_profile;

  /// No description provided for @image_not_supported.
  ///
  /// In en, this message translates to:
  /// **'Image not supported'**
  String get image_not_supported;

  /// No description provided for @subscription_plans.
  ///
  /// In en, this message translates to:
  /// **'Subscription Plans'**
  String get subscription_plans;

  /// No description provided for @manage.
  ///
  /// In en, this message translates to:
  /// **'Manage'**
  String get manage;

  /// No description provided for @active_subscription.
  ///
  /// In en, this message translates to:
  /// **'Active Subscription'**
  String get active_subscription;

  /// No description provided for @choose_plan.
  ///
  /// In en, this message translates to:
  /// **'Choose a Plan'**
  String get choose_plan;

  /// No description provided for @free.
  ///
  /// In en, this message translates to:
  /// **'Free'**
  String get free;

  /// No description provided for @per_month.
  ///
  /// In en, this message translates to:
  /// **'/ month'**
  String get per_month;

  /// No description provided for @per_year.
  ///
  /// In en, this message translates to:
  /// **'/ year'**
  String get per_year;

  /// No description provided for @current_plan.
  ///
  /// In en, this message translates to:
  /// **'Current Plan'**
  String get current_plan;

  /// No description provided for @get_started_free.
  ///
  /// In en, this message translates to:
  /// **'Get Started Free'**
  String get get_started_free;

  /// No description provided for @subscribe.
  ///
  /// In en, this message translates to:
  /// **'Subscribe'**
  String get subscribe;

  /// No description provided for @payment_success.
  ///
  /// In en, this message translates to:
  /// **'Payment successful! Your subscription is now active.'**
  String get payment_success;

  /// No description provided for @payment_failed.
  ///
  /// In en, this message translates to:
  /// **'Payment was cancelled or failed.'**
  String get payment_failed;

  /// No description provided for @error_prefix.
  ///
  /// In en, this message translates to:
  /// **'Error:'**
  String get error_prefix;

  /// No description provided for @my_subscription.
  ///
  /// In en, this message translates to:
  /// **'My Subscription'**
  String get my_subscription;

  /// No description provided for @cancel_subscription_title.
  ///
  /// In en, this message translates to:
  /// **'Cancel Subscription'**
  String get cancel_subscription_title;

  /// No description provided for @cancel_subscription_msg.
  ///
  /// In en, this message translates to:
  /// **'Your subscription will remain active until the end of the current billing period. Are you sure?'**
  String get cancel_subscription_msg;

  /// No description provided for @no.
  ///
  /// In en, this message translates to:
  /// **'No'**
  String get no;

  /// No description provided for @cancel_subscription_btn.
  ///
  /// In en, this message translates to:
  /// **'Cancel Subscription'**
  String get cancel_subscription_btn;

  /// No description provided for @subscription_cancelled.
  ///
  /// In en, this message translates to:
  /// **'Subscription cancelled.'**
  String get subscription_cancelled;

  /// No description provided for @cancel_failed.
  ///
  /// In en, this message translates to:
  /// **'Failed to cancel subscription.'**
  String get cancel_failed;

  /// No description provided for @active_plan.
  ///
  /// In en, this message translates to:
  /// **'Active Plan'**
  String get active_plan;

  /// No description provided for @expires.
  ///
  /// In en, this message translates to:
  /// **'Expires'**
  String get expires;

  /// No description provided for @no_active_subscription.
  ///
  /// In en, this message translates to:
  /// **'No Active Subscription'**
  String get no_active_subscription;

  /// No description provided for @choose_plan_get_started.
  ///
  /// In en, this message translates to:
  /// **'Choose a plan to get started'**
  String get choose_plan_get_started;

  /// No description provided for @view_plans.
  ///
  /// In en, this message translates to:
  /// **'View Plans'**
  String get view_plans;

  /// No description provided for @billing_history.
  ///
  /// In en, this message translates to:
  /// **'Billing History'**
  String get billing_history;

  /// No description provided for @no_invoices_yet.
  ///
  /// In en, this message translates to:
  /// **'No invoices yet'**
  String get no_invoices_yet;

  /// No description provided for @paid.
  ///
  /// In en, this message translates to:
  /// **'Paid'**
  String get paid;

  /// No description provided for @pending.
  ///
  /// In en, this message translates to:
  /// **'Pending'**
  String get pending;

  /// No description provided for @zarinpal_payment.
  ///
  /// In en, this message translates to:
  /// **'Zarinpal Payment'**
  String get zarinpal_payment;

  /// No description provided for @comments_title.
  ///
  /// In en, this message translates to:
  /// **'Comments'**
  String get comments_title;

  /// No description provided for @send.
  ///
  /// In en, this message translates to:
  /// **'Send'**
  String get send;

  /// No description provided for @write_comment_hint.
  ///
  /// In en, this message translates to:
  /// **'Write a comment...'**
  String get write_comment_hint;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['ar', 'en', 'fa', 'ru', 'tr'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'ar':
      return AppLocalizationsAr();
    case 'en':
      return AppLocalizationsEn();
    case 'fa':
      return AppLocalizationsFa();
    case 'ru':
      return AppLocalizationsRu();
    case 'tr':
      return AppLocalizationsTr();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
