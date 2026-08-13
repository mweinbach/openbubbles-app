import 'package:bluebubbles/app/layouts/setup/setup_view.dart';
import 'package:bluebubbles/helpers/backend/settings_helpers.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/setup/pages/page_template.dart';
import 'package:disable_battery_optimization/disable_battery_optimization.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:shimmer/shimmer.dart';

class BatteryOptimizationCheck extends StatelessWidget {
  const BatteryOptimizationCheck({super.key});

  SetupViewController get controller => Get.find<SetupViewController>();

  @override
  Widget build(BuildContext context) {
    return SetupPageTemplate(
      title: "Battery Optimization",
      subtitle:
          "We recommend disabling battery optimization for OpenBubbles to ensure you receive all your notifications.",
      onNextPressed: () async {
        if (!((await DisableBatteryOptimization.isAllBatteryOptimizationDisabled) ?? false)) {
          final optimizationsDisabled = await disableBatteryOptimizations();
          if (!optimizationsDisabled) {
            showSnackbar("Error", "Battery optimizations were not disabled. Please try again.");
          }
          if (!controller.triedBattery) {
            controller.triedBattery = true;
            return false;
          }
        }
        controller.triedBattery = false;
        return true;
      },
      belowSubtitle: FutureBuilder<bool?>(
        future: DisableBatteryOptimization.isAllBatteryOptimizationDisabled,
        initialData: false,
        builder: (context, snapshot) {
          bool disabled = snapshot.data != null && snapshot.data == true;
          if (disabled) {
            return Padding(
              padding: const EdgeInsets.all(8.0),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text("Battery Optimization: ${disabled ? "Disabled" : "Enabled"}",
                    style: context.theme.textTheme.bodyLarge!
                        .apply(
                          fontSizeDelta: 1.5,
                          color: disabled ? Colors.green : context.theme.colorScheme.error,
                        )
                        .copyWith(height: 2)),
              ),
            );
          } else {
            return const SizedBox.shrink();
          }
        },
      ),
    );
  }
}
