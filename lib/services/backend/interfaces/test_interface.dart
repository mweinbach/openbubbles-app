import 'package:bluebubbles/env.dart';
import 'package:bluebubbles/services/backend/actions/test_actions.dart';
import 'package:get_it/get_it.dart';
import 'package:bluebubbles/services/isolates/global_isolate.dart';

class TestInterface {
  static Future<String> testReturnInput() async {
    if (isIsolate) {
      return TestActions.executeTestReturnInput('Hello from TestIsolate');
    } else {
      return await GetIt.I<GlobalIsolate>()
          .send<String>(IsolateRequestType.testReturnInput, input: 'Hello from TestIsolate');
    }
  }
}
