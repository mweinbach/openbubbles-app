class TestActions {
  static String executeTestReturnInput(String input) {
    return 'Test action executed with input: $input';
  }

  static void executeTestPrintInput(String input) {
    print('Test action executed with input: $input');
  }

  static void executeTestThrowError(String input) {
    throw Exception('Test action error with input: $input');
  }
}
