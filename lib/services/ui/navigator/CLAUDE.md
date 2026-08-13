# services/ui/navigator/ — Navigation Service

## File
`navigator_service.dart` — `NavigatorService` (shorthand: `NavigationSvc`)

## Responsibilities
GetX-based navigation helper. Always use this instead of calling `Navigator.of(context)` directly in feature code.

## Key Methods
```dart
NavigationSvc.push(context, widget)                 // push route
NavigationSvc.pushAndRemoveUntil(context, widget)   // push + clear stack
NavigationSvc.pop(context)                          // pop current route
NavigationSvc.pushSettings(context)                 // shortcut to settings page
```

## Why Not Navigator.of(context)
- Provides a single place to add route logging, analytics, and guard conditions
- Works correctly in contexts where `BuildContext` may not have the right `Navigator` ancestor
- Allows navigation from service-layer code without a context (via `Get.key`)

## Related
- Navigation from backend: use `EventDispatcherSvc.emit('navigate', ...)` + widget listener
- Route definitions: `lib/main.dart`
