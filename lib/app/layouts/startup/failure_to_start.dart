import 'package:bluebubbles/app/wrappers/bb_scaffold.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class FailureToStart extends StatelessWidget {
  const FailureToStart({super.key, this.e, this.s, this.otherTitle});
  final dynamic e;
  final StackTrace? s;
  final String? otherTitle;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'OpenBubbles',
      home: AnnotatedRegion<SystemUiOverlayStyle>(
        value: const SystemUiOverlayStyle(
          systemNavigationBarColor: Colors.black,
          systemNavigationBarIconBrightness: Brightness.light,
          statusBarIconBrightness: Brightness.light,
          statusBarColor: Colors.transparent,
        ),
        child: BBScaffold(
          backgroundColor: Colors.black,
          body: Padding(
            padding: const EdgeInsets.all(8.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              mainAxisSize: MainAxisSize.max,
              children: [
                Center(
                  child: Text(
                    otherTitle ??
                        "Whoops, looks like we messed up. Unfortunately you will need to reinstall the app, sorry for the inconvenience :(",
                    style: const TextStyle(color: Colors.white, fontSize: 30),
                    textAlign: TextAlign.center,
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(top: 20.0),
                  child: Center(
                    child: Text("Error: ${e.toString()}", style: const TextStyle(color: Colors.white, fontSize: 10)),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(top: 20.0),
                  child: Center(
                    child:
                        Text("Stacktrace: ${s.toString()}", style: const TextStyle(color: Colors.white, fontSize: 10)),
                  ),
                )
              ],
            ),
          ),
        ),
      ),
    );
  }
}
