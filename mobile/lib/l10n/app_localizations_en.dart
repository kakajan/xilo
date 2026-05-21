// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get app_title => 'Xilo';

  @override
  String get sign_in => 'Sign In';

  @override
  String get sign_up => 'Sign Up';

  @override
  String get email => 'Email';

  @override
  String get password => 'Password';

  @override
  String get username => 'Username';

  @override
  String get required => 'Required';

  @override
  String min_characters(int count) {
    return 'Min $count characters';
  }

  @override
  String get signing_in => 'Signing in...';

  @override
  String get dont_have_account => 'Don\'t have an account? Sign Up';

  @override
  String get already_have_account => 'Already have an account? Sign In';

  @override
  String get creating_account => 'Creating...';

  @override
  String get create_account => 'Create Account';

  @override
  String get search => 'Search';

  @override
  String get search_posts_placeholder => 'Search posts...';

  @override
  String get type_to_search => 'Type to search';

  @override
  String get xilo => 'Xilo';

  @override
  String failed_to_load(String item) {
    return 'Failed to load $item. Please try again.';
  }

  @override
  String get no_posts_yet => 'No posts yet';

  @override
  String get check_back_later => 'Check back later for new content';

  @override
  String get premium => 'Premium';

  @override
  String get unknown => 'Unknown';

  @override
  String min_read(int minutes) {
    return '$minutes min read';
  }

  @override
  String get just_now => 'Just now';

  @override
  String minutes_ago(int m) {
    return '${m}m ago';
  }

  @override
  String hours_ago(int h) {
    return '${h}h ago';
  }

  @override
  String days_ago(int d) {
    return '${d}d ago';
  }

  @override
  String get retry => 'Retry';

  @override
  String get failed_to_load_post => 'Failed to load post';

  @override
  String get comments => 'Comments';

  @override
  String get no_comments_yet => 'No comments yet. Be the first!';

  @override
  String get write => 'Write';

  @override
  String get post_title_hint => 'Post title';

  @override
  String get tell_story_hint => 'Tell your story...';

  @override
  String get publish => 'Publish';

  @override
  String get notifications => 'Notifications';

  @override
  String get no_notifications_yet => 'No notifications yet';

  @override
  String get bookmarks => 'Bookmarks';

  @override
  String get no_bookmarks_yet => 'No bookmarks yet';

  @override
  String get settings => 'Settings';

  @override
  String get profile => 'Profile';

  @override
  String get billing_subscription => 'Billing & Subscription';

  @override
  String get appearance => 'Appearance';

  @override
  String get language => 'Language';

  @override
  String get select_language => 'Select Language';

  @override
  String get about => 'About';

  @override
  String get follow => 'Follow';

  @override
  String get unfollow => 'Unfollow';

  @override
  String get display_name => 'Display Name';

  @override
  String get bio_placeholder => 'Bio goes here';

  @override
  String get nav_home => 'Home';

  @override
  String get nav_search => 'Search';

  @override
  String get nav_write => 'Write';

  @override
  String get nav_notifications => 'Notifications';

  @override
  String get nav_profile => 'Profile';

  @override
  String get image_not_supported => 'Image not supported';

  @override
  String get subscription_plans => 'Subscription Plans';

  @override
  String get manage => 'Manage';

  @override
  String get active_subscription => 'Active Subscription';

  @override
  String get choose_plan => 'Choose a Plan';

  @override
  String get free => 'Free';

  @override
  String get per_month => '/ month';

  @override
  String get per_year => '/ year';

  @override
  String get current_plan => 'Current Plan';

  @override
  String get get_started_free => 'Get Started Free';

  @override
  String get subscribe => 'Subscribe';

  @override
  String get payment_success =>
      'Payment successful! Your subscription is now active.';

  @override
  String get payment_failed => 'Payment was cancelled or failed.';

  @override
  String get error_prefix => 'Error:';

  @override
  String get my_subscription => 'My Subscription';

  @override
  String get cancel_subscription_title => 'Cancel Subscription';

  @override
  String get cancel_subscription_msg =>
      'Your subscription will remain active until the end of the current billing period. Are you sure?';

  @override
  String get no => 'No';

  @override
  String get cancel_subscription_btn => 'Cancel Subscription';

  @override
  String get subscription_cancelled => 'Subscription cancelled.';

  @override
  String get cancel_failed => 'Failed to cancel subscription.';

  @override
  String get active_plan => 'Active Plan';

  @override
  String get expires => 'Expires';

  @override
  String get no_active_subscription => 'No Active Subscription';

  @override
  String get choose_plan_get_started => 'Choose a plan to get started';

  @override
  String get view_plans => 'View Plans';

  @override
  String get billing_history => 'Billing History';

  @override
  String get no_invoices_yet => 'No invoices yet';

  @override
  String get paid => 'Paid';

  @override
  String get pending => 'Pending';

  @override
  String get zarinpal_payment => 'Zarinpal Payment';

  @override
  String get comments_title => 'Comments';

  @override
  String get send => 'Send';

  @override
  String get write_comment_hint => 'Write a comment...';

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
  String get share_profile => 'Share Profile';

  @override
  String get message_coming_soon => 'Messaging is coming soon';
}
