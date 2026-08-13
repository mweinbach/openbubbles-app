import 'dart:io';
import 'dart:math';

import 'package:adaptive_theme/adaptive_theme.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/next_button.dart';
import 'package:bluebubbles/app/wrappers/bb_app_bar.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:get/get.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';

class AboutPanel extends StatefulWidget {
  const AboutPanel({super.key});

  @override
  State<StatefulWidget> createState() => _AboutPanelState();
}

class _AboutPanelState extends State<AboutPanel> with ThemeHelpers {
  Widget buildFormatted(String format) {
    final splice = format.split("**");
    bool bold = false;
    return RichText(
      text: TextSpan(
        style: context.theme.textTheme.bodyMedium?.apply(fontSizeFactor: 1.1),
        children: splice.map((item) {
          bold = !bold;
          return TextSpan(
            text: item,
            style: !bold ? const TextStyle(fontWeight: FontWeight.bold) : null,
          );
        }).toList(),
      ),
    );
  }

  AdaptiveThemeMode? mode;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final isDark = context.theme.brightness == Brightness.dark;
      if (!isDark) {
        mode = AdaptiveTheme.of(context).mode;
        AdaptiveTheme.of(context).setDark();
      }
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mode != null && Get.context != null) {
        AdaptiveTheme.of(Get.context!).setThemeMode(mode!);
      }
    });
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SettingsScaffold(
        title: "About & Links",
        initialHeader: null,
        iosSubtitle: iosSubtitle,
        materialSubtitle: materialSubtitle,
        tileColor: tileColor,
        headerColor: headerColor,
        bodySlivers: [
          SliverList(
            delegate: SliverChildListDelegate(
              <Widget>[
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      RichText(
                        text: TextSpan(
                          style: context.theme.textTheme.titleLarge,
                          children: const [
                            TextSpan(text: "Would you "),
                            TextSpan(
                              text: "see it?",
                              style: TextStyle(fontStyle: FontStyle.italic, fontWeight: FontWeight.bold),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 15),
                      buildFormatted("It **isn't obvious**"),
                      const SizedBox(height: 15),
                      buildFormatted("It **rarely looks like the movies**"),
                      const SizedBox(height: 15),
                      buildFormatted(
                          "It's a **good, normal person** who isn't fully aware of what they are doing. They could **even be your friend**"),
                      const SizedBox(height: 15),
                      buildFormatted(
                          "It **doesn't have to be physical or even spoken**. It just has to be a **clear and consistent message**"),
                      const SizedBox(height: 25),
                      RichText(
                        text: TextSpan(
                          style: context.theme.textTheme.bodyMedium?.apply(fontSizeFactor: 1.2),
                          children: const [
                            TextSpan(text: "You deserve to be treated with respect and kindness. "),
                            TextSpan(
                              text: "Unwarranted, recurring, and targeted hostility in the classroom or workplace ",
                              style: TextStyle(fontWeight: FontWeight.bold),
                            ),
                            TextSpan(
                              text: "is bullying and is never acceptable",
                              style: TextStyle(fontStyle: FontStyle.italic, fontWeight: FontWeight.bold),
                            ),
                            TextSpan(text: "."),
                          ],
                        ),
                      ),
                      const SizedBox(height: 25),
                      buildFormatted(
                          "**Anyone can be a victim.** **Anyone can be a perpetrator.** If something is happening, know there is **no excuse, especially if society has normalized the behavior outside a group.** You are **not the problem.** They **don't have to treat you this way.** It **won't fix itself.**"),
                      const SizedBox(height: 15),
                      buildFormatted(
                          "**Stand up for what is right. Do not attack the bully, assert the behavior is unacceptable.** There are no winners or losers. There is a bully who needs help. Abusing others is unacceptable and isn't going to get them what they need. We owe it to everyone to hold us accountable for our actions and help us grow."),
                      const SizedBox(height: 15),
                      Text("We can fix this together",
                          style: context.theme.textTheme.bodyMedium?.apply(fontWeightDelta: 2, fontSizeFactor: 1.2)),
                      RichText(
                        text: TextSpan(
                          style: context.theme.textTheme.bodyMedium?.apply(fontWeightDelta: 2, fontSizeFactor: 1.2),
                          children: [
                            TextSpan(
                              text: 'stopbullying.gov',
                              style: const TextStyle(color: Colors.blue),
                              recognizer: TapGestureRecognizer()
                                ..onTap = () {
                                  launchUrl(Uri.parse("https://www.stopbullying.gov/"),
                                      mode: LaunchMode.externalApplication);
                                },
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Links"),
                SettingsSection(
                  backgroundColor: tileColor,
                  children: [
                    SettingsTile(
                        title: "OpenBubbles Website",
                        subtitle: "Visit the OpenBubbles Homepage",
                        onTap: () async {
                          await launchUrl(Uri(scheme: "https", host: "openbubbles.app"),
                              mode: LaunchMode.externalApplication);
                        },
                        leading: const SettingsLeadingIcon(
                          iosIcon: CupertinoIcons.globe,
                          materialIcon: Icons.language,
                          containerColor: Colors.green,
                        ),
                        trailing: const NextButton()),
                    const SettingsDivider(),
                    SettingsTile(
                        title: "Make a Donation",
                        subtitle:
                            "Support the developers by making a one-time or recurring donation to the OpenBubbles Team!",
                        onTap: () async {
                          await launchUrl(Uri.parse("https://www.paypal.com/donate/?hosted_button_id=MUZH6LH6ZGN6G"),
                              mode: LaunchMode.externalApplication);
                        },
                        leading: const SettingsLeadingIcon(
                          iosIcon: CupertinoIcons.money_dollar_circle,
                          materialIcon: Icons.attach_money,
                          containerColor: Colors.green,
                        )),
                    const SettingsDivider(),
                    SettingsTile(
                        title: "Documentation",
                        subtitle: "Learn how to use OpenBubbles or fix common issues",
                        onTap: () async {
                          await launchUrl(Uri(scheme: "https", host: "openbubbles.app", path: "docs/faq.html"),
                              mode: LaunchMode.externalApplication);
                        },
                        leading: const SettingsLeadingIcon(
                          iosIcon: CupertinoIcons.doc_append,
                          materialIcon: Icons.document_scanner,
                          containerColor: Colors.blueAccent,
                        ),
                        trailing: const NextButton()),
                    const SettingsDivider(),
                    SettingsTile(
                        title: "Source Code",
                        subtitle: "View the source code for OpenBubbles, and contribute!",
                        onTap: () async {
                          await launchUrl(Uri(scheme: "https", host: "github.com", path: "OpenBubbles/openbubbles-app"),
                              mode: LaunchMode.externalApplication);
                        },
                        onLongPress: () async {
                          await launchUrl(
                              Uri(scheme: "https", host: "github.com", path: "OpenBubbles/openbubbles-app/issues"),
                              mode: LaunchMode.externalApplication);
                        },
                        leading: const SettingsLeadingIcon(
                          iosIcon: CupertinoIcons.chevron_left_slash_chevron_right,
                          materialIcon: Icons.code,
                          containerColor: Colors.orange,
                        ),
                        trailing: const NextButton()),
                    const SettingsDivider(),
                    SettingsTile(
                        title: "OpenStreetMap",
                        subtitle: "Used for geocoding services",
                        onTap: () async {
                          await launchUrl(Uri.parse("https://openstreetmap.org/copyright"),
                              mode: LaunchMode.externalApplication);
                        },
                        leading: const SettingsLeadingIcon(
                          iosIcon: CupertinoIcons.globe,
                          materialIcon: Icons.language,
                          containerColor: Colors.green,
                        ),
                        trailing: const NextButton()),
                    const SettingsDivider(),
                    SettingsTile(
                        title: "Report a Bug",
                        subtitle: "Found a bug? Report it here!",
                        onTap: () async {
                          await launchUrl(
                              Uri(scheme: "https", host: "github.com", path: "OpenBubbles/openbubbles-app/issues"),
                              mode: LaunchMode.externalApplication);
                        },
                        leading: const SettingsLeadingIcon(
                          iosIcon: CupertinoIcons.triangle_righthalf_fill,
                          materialIcon: Icons.bug_report,
                          containerColor: Colors.redAccent,
                        ),
                        trailing: const NextButton()),
                  ],
                ),
                SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Info"),
                SettingsSection(
                  backgroundColor: tileColor,
                  children: [
                    SettingsTile(
                      title: "Changelog (BlueBubbles)",
                      onTap: () async {
                        String changelog =
                            await DefaultAssetBundle.of(context).loadString('assets/changelog/changelog.md');
                        Navigator.of(context).push(
                          ThemeSwitcher.buildPageRoute(
                            builder: (context) => Scaffold(
                              body: Markdown(
                                data: changelog,
                                physics: const AlwaysScrollableScrollPhysics(
                                  parent: BouncingScrollPhysics(),
                                ),
                                styleSheet: MarkdownStyleSheet.fromTheme(
                                  context.theme
                                    ..textTheme.copyWith(
                                      headlineMedium: const TextStyle(
                                        color: Colors.white,
                                      ),
                                    ),
                                ).copyWith(
                                  h1: context.theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.bold),
                                  h2: context.theme.textTheme.titleMedium!.copyWith(fontWeight: FontWeight.bold),
                                  h3: context.theme.textTheme.titleSmall!.copyWith(
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                              backgroundColor: context.theme.colorScheme.surface,
                              appBar: BBAppBar(
                                toolbarHeight: 50,
                                leading: buildBackButton(context),
                                iconTheme: IconThemeData(color: context.theme.colorScheme.primary),
                                title: Padding(
                                  padding: EdgeInsets.only(top: kIsDesktop ? 20 : 0),
                                  child: Text(
                                    "Changelog",
                                    style: context.theme.textTheme.titleLarge,
                                  ),
                                ),
                              ),
                            ),
                          ),
                        );
                      },
                      subtitle: "See what's new in the latest version",
                      leading: const SettingsLeadingIcon(
                        iosIcon: CupertinoIcons.doc_plaintext,
                        materialIcon: Icons.article,
                        containerColor: Colors.blueAccent,
                      ),
                    ),
                    const SettingsDivider(),
                    SettingsTile(
                      title: "Developers",
                      onTap: () {
                        final devs = {
                          "Zach (BlueBubbles)": "zlshames",
                          "Tanay (BlueBubbles)": "tneotia",
                          "Joel (BlueBubbles)": "jjoelj",
                          "Tae": "TaeHagen",
                        };
                        showBBDialog(
                          context: context,
                          title: "GitHub Profiles",
                          content: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: devs.entries
                                .map((e) => Container(
                                      alignment: Alignment.center,
                                      padding: const EdgeInsets.all(8),
                                      child: RichText(
                                        text: TextSpan(
                                            text: e.key,
                                            style: context.theme.textTheme.bodyLarge!.copyWith(
                                                decoration: TextDecoration.underline,
                                                color: context.theme.colorScheme.primary),
                                            recognizer: TapGestureRecognizer()
                                              ..onTap = () async {
                                                await launchUrl(Uri(scheme: "https", host: "github.com", path: e.value),
                                                    mode: LaunchMode.externalApplication);
                                              }),
                                      ),
                                    ))
                                .toList(),
                          ),
                          actions: [
                            BBDialogAction(
                              text: "Close",
                              onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
                            ),
                          ],
                        );
                      },
                      subtitle: "Meet the developers behind OpenBubbles",
                      leading: const SettingsLeadingIcon(
                        iosIcon: CupertinoIcons.person_alt,
                        materialIcon: Icons.person,
                        containerColor: Colors.green,
                      ),
                    ),
                    if (kIsWeb || kIsDesktop) const SettingsDivider(),
                    if (kIsWeb || kIsDesktop)
                      SettingsTile(
                        title: "Keyboard Shortcuts",
                        onTap: () {
                          showBBDialog(
                            context: context,
                            title: "Keyboard Shortcuts",
                            content: SizedBox(
                              height: MediaQuery.of(context).size.height / 2,
                              child: SingleChildScrollView(
                                child: DataTable(
                                  columnSpacing: 5,
                                  dataRowMinHeight: 75,
                                  dataRowMaxHeight: 75,
                                  dataTextStyle: context.theme.textTheme.bodyLarge,
                                  headingTextStyle:
                                      context.theme.textTheme.bodyLarge!.copyWith(fontStyle: FontStyle.italic),
                                  columns: const <DataColumn>[
                                    DataColumn(
                                      label: Text(
                                        'Key Combination',
                                      ),
                                    ),
                                    DataColumn(
                                      label: Text(
                                        'Action',
                                      ),
                                    ),
                                  ],
                                  rows: const <DataRow>[
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('CTRL + COMMA')),
                                        DataCell(Text('Open settings')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('CTRL + N')),
                                        DataCell(Text('Start new chat (Desktop only)')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('ALT + N')),
                                        DataCell(Text('Start new chat')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('CTRL + F')),
                                        DataCell(Text('Open search page')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('ALT + R')),
                                        DataCell(Text('Reply to most recent message in the currently selected chat')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('CTRL + R')),
                                        DataCell(Text(
                                            'Reply to most recent message in the currently selected chat (Desktop only)')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('ALT + G')),
                                        DataCell(Text('Sync from server')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('CTRL + SHIFT + R')),
                                        DataCell(Text('Sync from server (Desktop only)')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('CTRL + G')),
                                        DataCell(Text('Sync from server (Desktop only)')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('CTRL + SHIFT + 1-6')),
                                        DataCell(Text(
                                            'Apply reaction to most recent message in the currently selected chat')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('CTRL + ARROW DOWN')),
                                        DataCell(Text('Switch to the chat below the currently selected one')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('CTRL + TAB')),
                                        DataCell(
                                            Text('Switch to the chat below the currently selected one (Desktop only)')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('CTRL + ARROW UP')),
                                        DataCell(Text('Switch to the chat above the currently selected one')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('CTRL + SHIFT + TAB')),
                                        DataCell(
                                            Text('Switch to the chat above the currently selected one (Desktop only)')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('CTRL + I')),
                                        DataCell(Text('Open chat details page')),
                                      ],
                                    ),
                                    DataRow(
                                      cells: <DataCell>[
                                        DataCell(Text('ESC')),
                                        DataCell(Text('Close pages')),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                            ),
                            actions: [
                              BBDialogAction(
                                text: "Close",
                                onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
                              ),
                            ],
                          );
                        },
                        leading: const SettingsLeadingIcon(
                          iosIcon: CupertinoIcons.keyboard,
                          materialIcon: Icons.keyboard,
                        ),
                      ),
                    const SettingsDivider(),
                    SettingsTile(
                      title: "About",
                      subtitle: "Version and other information",
                      onTap: () {
                        showDialog<void>(
                          context: context,
                          builder: (BuildContext context) {
                            return FutureBuilder<PackageInfo>(
                                future: PackageInfo.fromPlatform(),
                                builder: (BuildContext context, AsyncSnapshot<PackageInfo> snapshot) {
                                  return AlertDialog(
                                    contentPadding: const EdgeInsets.only(
                                      top: 24,
                                      left: 24,
                                      right: 24,
                                    ),
                                    elevation: 10.0,
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(
                                        10.0,
                                      ),
                                    ),
                                    scrollable: true,
                                    backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                    content: ListBody(
                                      children: <Widget>[
                                        Row(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: <Widget>[
                                            IconTheme(
                                              data: context.theme.iconTheme,
                                              child: Image.asset(
                                                "assets/icon/icon.png",
                                                width: 30,
                                                height: 30,
                                              ),
                                            ),
                                            Expanded(
                                              child: Padding(
                                                padding: const EdgeInsets.symmetric(horizontal: 24.0),
                                                child: ListBody(
                                                  children: <Widget>[
                                                    Text(
                                                      "OpenBubbles",
                                                      style: context.theme.textTheme.titleLarge,
                                                    ),
                                                    Text("Based on BlueBubbles (not affiliated)",
                                                        style: context.theme.textTheme.bodyMedium),
                                                    if (!kIsDesktop)
                                                      Text("Version Number: ",
                                                          style: context.theme.textTheme.bodyLarge),
                                                    if (!kIsDesktop)
                                                      Text(
                                                          "Version Code: ${snapshot.hasData ? snapshot.data!.buildNumber.toString().lastChars(min(4, snapshot.data!.buildNumber.length)) : "N/A"}",
                                                          style: context.theme.textTheme.bodyLarge),
                                                    if (kIsDesktop)
                                                      Text(
                                                        "${FilesystemSvc.packageInfo.version}_${Platform.operatingSystem.capitalizeFirst!}${isSnap ? "_Snap" : isFlatpak ? "_Flatpak" : isMsix ? "_Msix" : ""}",
                                                        style: context.theme.textTheme.bodyLarge,
                                                      ),
                                                  ],
                                                ),
                                              ),
                                            ),
                                          ],
                                        ),
                                      ],
                                    ),
                                    actions: <Widget>[
                                      TextButton(
                                        child: Text("View Licenses",
                                            style: context.theme.textTheme.bodyLarge!
                                                .copyWith(color: context.theme.colorScheme.primary)),
                                        onPressed: () {
                                          Navigator.of(context).push(MaterialPageRoute<void>(
                                            builder: (BuildContext context) => Theme(
                                              data: context.theme,
                                              child: LicensePage(
                                                applicationName: "BlueBubbles",
                                                applicationVersion: snapshot.hasData ? snapshot.data!.version : "",
                                                applicationIcon: Image.asset(
                                                  "assets/icon/icon.png",
                                                  width: 30,
                                                  height: 30,
                                                ),
                                              ),
                                            ),
                                          ));
                                        },
                                      ),
                                      TextButton(
                                        child: Text("Close",
                                            style: context.theme.textTheme.bodyLarge!
                                                .copyWith(color: context.theme.colorScheme.primary)),
                                        onPressed: () {
                                          Navigator.pop(context);
                                        },
                                      ),
                                    ],
                                  );
                                });
                          },
                        );
                      },
                      leading: const SettingsLeadingIcon(
                        iosIcon: CupertinoIcons.info_circle,
                        materialIcon: Icons.info,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ]);
  }
}
