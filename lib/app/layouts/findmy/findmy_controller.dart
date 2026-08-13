import 'dart:async';

import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/app/layouts/findmy/findmy_location_clipper.dart';
import 'package:bluebubbles/app/layouts/findmy/findmy_pin_clipper.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/models/models.dart' show HandleLookupKey;
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_map_marker_popup/flutter_map_marker_popup.dart';
import 'package:geolocator/geolocator.dart';
import 'package:get/get.dart';
import 'package:latlong2/latlong.dart';
import 'package:sliding_up_panel2/sliding_up_panel2.dart';
import 'package:universal_io/io.dart';

class FindMyController extends GetxController {
  FindMyController({this.defaultFriend});

  String? defaultFriend;

  final ScrollController devicesController = ScrollController();
  final ScrollController itemsController = ScrollController();
  final ScrollController friendsController = ScrollController();

  final PopupController popupController = PopupController();
  final MapController mapController = MapController();
  final PanelController panelController = PanelController();
  final completer = Completer<void>();

  TabController? tabController;

  final RxInt tabIndex = 0.obs;
  final RxList<FindMyDevice> devices = <FindMyDevice>[].obs;
  final RxList<FindMyFriend> friends = <FindMyFriend>[].obs;
  final RxList<FindMyFriend> friendsWithLocation = <FindMyFriend>[].obs;
  final RxList<FindMyFriend> friendsWithoutLocation = <FindMyFriend>[].obs;
  final RxMap<String, Marker> markers = <String, Marker>{}.obs;
  final Rxn<Position> location = Rxn<Position>();
  final Rxn<bool> fetching = Rxn<bool>(true);
  final RxBool refreshing = false.obs;
  final Rxn<bool> fetching2 = Rxn<bool>(true);
  final RxBool refreshing2 = false.obs;
  final RxBool canRefresh = false.obs;
  final RxBool hasMovedToCurrentLocation = false.obs;
  final RxBool isInClique = true.obs;

  final Map<(double, double), Address> cachedAddresses = {};
  final RxList<api.DartBeacon> cachedBeacons = <api.DartBeacon>[].obs;

  StreamSubscription? locationSub;
  Timer? myTimer;
  Timer? _refreshTimer;
  DateTime? beaconCacheDate;
  api.FindMyFriendsClientDefaultAnisetteProvider? fmfClient;
  api.FindMyPhoneClientDefaultAnisetteProvider? fmipClient;

  @override
  void onInit() {
    super.onInit();
    if (defaultFriend != null) {
      tabIndex.value = 1;
    }

    getLocations();
    myTimer = Timer.periodic(const Duration(seconds: 5), (_) => getLocations());

    SocketSvc.socket?.on("new-findmy-location", _handleNewFindMyLocation);

    _scheduleRefreshGate();
  }

  bool get _isAlive => !isClosed;

  void _scheduleRefreshGate() {
    _refreshTimer?.cancel();
    _refreshTimer = Timer(const Duration(seconds: 30), () {
      if (!_isAlive) return;
      canRefresh.value = true;
    });
  }

  void _handleNewFindMyLocation(dynamic data) {
    if (!_isAlive) return;
    try {
      final friend = FindMyFriend.fromJson(data);
      Logger.info("Received new location for ${friend.handle?.address}");
      if ((friend.latitude ?? 0) == 0 && (friend.longitude ?? 0) == 0) return;

      final existingFriendIndex = friends.indexWhere((e) => e.stableId != null && e.stableId == friend.stableId);
      final existingFriend = existingFriendIndex == -1 ? null : friends[existingFriendIndex];

      final shouldUpdate = existingFriend == null ||
          existingFriend.status == null ||
          friend.locatingInProgress ||
          LocationStatus.values.indexOf(existingFriend.status!) <=
              LocationStatus.values.indexOf(friend.status ?? LocationStatus.legacy);

      if (shouldUpdate) {
        Logger.info("Updating map for ${friend.stableId}");
        if (existingFriendIndex == -1) {
          friends.add(friend);
        } else {
          friends[existingFriendIndex] = friend;
        }

        friendsWithLocation.value =
            friends.where((item) => (item.latitude ?? 0) != 0 && (item.longitude ?? 0) != 0).toList();
        friendsWithoutLocation.value =
            friends.where((item) => (item.latitude ?? 0) == 0 && (item.longitude ?? 0) == 0).toList();
        buildFriendMarker(friend);
      }
    } catch (e, s) {
      Logger.warn("Failed to fetch FindMy locations", error: e, trace: s, tag: 'FindMyController');
    }
  }

  Future<void> getLocations({bool refreshFriends = true, bool refreshDevices = true}) async {
    if (!_isAlive) return;

    if (!(Platform.isLinux && !kIsWeb)) {
      LocationPermission granted = await Geolocator.checkPermission();
      if (!_isAlive) return;
      if (granted == LocationPermission.denied) {
        granted = await Geolocator.requestPermission();
        if (!_isAlive) return;
      }

      if (granted == LocationPermission.whileInUse || granted == LocationPermission.always) {
        Geolocator.getCurrentPosition(locationSettings: const LocationSettings(timeLimit: Duration(seconds: 30)))
            .then((loc) {
          if (!_isAlive) return;
          location.value = loc;
          if (!hasMovedToCurrentLocation.value) {
            mapController.move(LatLng(loc.latitude, loc.longitude), 10);
            hasMovedToCurrentLocation.value = true;
          }
          SettingsSvc.settings.lastLocation.value = "${loc.latitude},${loc.longitude}";
          SettingsSvc.settings.saveOneAsync('lastLocation');
          buildLocationMarker(loc);

          if (!kIsDesktop && locationSub == null) {
            locationSub = Geolocator.getPositionStream().listen((event) {
              if (!_isAlive) return;
              location.value = event;
              SettingsSvc.settings.lastLocation.value = "${event.latitude},${event.longitude}";
              SettingsSvc.settings.saveOneAsync('lastLocation');
              buildLocationMarker(event);

              if (!hasMovedToCurrentLocation.value) {
                mapController.move(LatLng(event.latitude, event.longitude), 10);
                hasMovedToCurrentLocation.value = true;
              }
            });
          }
        });
      }
    }

    final isNewFriendsClient = fmfClient == null;
    fmfClient ??= await api.makeFindMyFriends(
      path: PushSvc.statePath,
      config: PushSvc.state!.osConfig,
      aps: PushSvc.state!.conn,
      anisette: PushSvc.state!.anisette,
      provider: PushSvc.state!.icloudServices!.tokenProvider,
    );
    if (!_isAlive) return;

    try {
      if (refreshFriends && !isNewFriendsClient) {
        await api.refreshFollowing(config: PushSvc.state!.osConfig, client: fmfClient!);
      }

      final following = await api.getFollowing(client: fmfClient!);
      friends.value = following
          .map((e) => FindMyFriend(
                latitude: e.lastLocation?.latitude,
                longitude: e.lastLocation?.longitude,
                longAddress: e.lastLocation?.address?.formattedAddressLines?.join("\n"),
                shortAddress: e.lastLocation?.address != null
                    ? "${e.lastLocation?.address?.locality}, ${e.lastLocation?.address?.stateCode ?? e.lastLocation?.address?.countryCode}"
                    : null,
                title: null,
                subtitle: null,
                handle:
                    Handle.findOne(addressAndService: HandleLookupKey(e.invitationAcceptedHandles.first, "iMessage")) ??
                        Handle(address: e.invitationAcceptedHandles.first),
                handleAddress: e.invitationAcceptedHandles.first,
                lastUpdated: e.lastLocation?.timestamp != null
                    ? DateTime.fromMillisecondsSinceEpoch(e.lastLocation!.timestamp)
                    : null,
                status: null,
                locatingInProgress: false,
                id: e.id,
              ))
          .toList();

      friendsWithLocation.value =
          friends.where((item) => (item.latitude ?? 0) != 0 && (item.longitude ?? 0) != 0).toList();
      friendsWithoutLocation.value =
          friends.where((item) => (item.latitude ?? 0) == 0 && (item.longitude ?? 0) == 0).toList();

      for (final friend in friendsWithLocation) {
        buildFriendMarker(friend);
      }
      fetching2.value = false;
      refreshing2.value = false;

      if (defaultFriend != null) {
        final friend = friends.firstWhereOrNull((item) => item.id == defaultFriend);
        defaultFriend = null;
        if (friend != null) {
          if (Get.context!.isPhone) {
            await panelController.close();
          }
          await completer.future;
          await api.selectFriend(config: PushSvc.state!.osConfig, client: fmfClient!, friend: friend.id);

          if (friend.latitude != null && friend.longitude != null) {
            final marker = markers.values.firstWhere(
              (e) => (e.key as ValueKey?)?.value == "friend-${friend.handle?.uniqueAddressAndService}",
            );
            popupController.showPopupsOnlyFor([marker]);
            mapController.move(LatLng(friend.latitude!, friend.longitude!), 10);
          }
        }
      }
    } catch (e, s) {
      Logger.error("Failed to parse FindMy Friends location data!", error: e, trace: s);
      fetching2.value = null;
      refreshing2.value = false;
      return;
    }

    final isNewDeviceClient = fmipClient == null;
    fmipClient ??= await api.makeFindMyPhone(
      config: PushSvc.state!.osConfig,
      path: PushSvc.statePath,
      aps: PushSvc.state!.conn,
      anisette: PushSvc.state!.anisette,
      provider: PushSvc.state!.icloudServices!.tokenProvider,
    );
    if (!_isAlive) return;

    try {
      if (refreshDevices && !isNewDeviceClient) {
        await api.refreshDevices(config: PushSvc.state!.osConfig, client: fmipClient!);
      }

      final foundDevices = await api.getDevices(client: fmipClient!);
      final mappedDevices = foundDevices
          .map((e) => FindMyDevice(
                deviceModel: e.deviceModel,
                lowPowerMode: e.lowPowerMode,
                passcodeLength: e.passcodeLength,
                itemGroup: null,
                id: e.id,
                batteryStatus: e.batteryStatus,
                audioChannels: const [],
                lostModeCapable: e.lostModeCapable,
                snd: null,
                batteryLevel: e.batteryLevel,
                locationEnabled: e.locationEnabled,
                isConsideredAccessory: e.isConsideredAccessory ?? false,
                address: null,
                location: e.location != null
                    ? Location(
                        positionType: e.location!.positionType,
                        verticalAccuracy: e.location!.verticalAccuracy.round(),
                        longitude: e.location!.longitude,
                        floorLevel: e.location!.floorLevel,
                        isInaccurate: e.location!.isInaccurate,
                        isOld: e.location!.isOld,
                        horizontalAccuracy: e.location!.horizontalAccuracy,
                        latitude: e.location!.latitude,
                        timeStamp: e.location!.timestamp,
                        altitude: e.location!.altitude.round(),
                        locationFinished: e.location!.locationFinished,
                      )
                    : null,
                modelDisplayName: e.modelDisplayName,
                deviceColor: e.deviceColor,
                activationLocked: e.activationLocked,
                rm2State: e.rm2State,
                locFoundEnabled: e.locFoundEnabled,
                nwd: e.nwd,
                deviceStatus: e.deviceStatus,
                remoteWipe: null,
                fmlyShare: e.fmlyShare,
                thisDevice: e.thisDevice,
                lostDevice: null,
                lostModeEnabled: e.lostModeEnabled,
                deviceDisplayName: e.deviceDisplayName,
                safeLocations: const [],
                name: e.name,
                canWipeAfterLock: e.canWipeAfterLock,
                isMac: e.isMac,
                rawDeviceModel: e.rawDeviceModel,
                baUuid: e.baUuid,
                trackingInfo: null,
                features: e.features,
                deviceDiscoveryId: e.deviceDiscoveryId,
                prsId: null,
                scd: e.scd,
                locationCapable: e.locationCapable,
                remoteLock: null,
                wipeInProgress: e.wipeInProgress,
                darkWake: e.darkWake,
                deviceWithYou: e.deviceWithYou,
                maxMsgChar: e.maxMsgChar,
                deviceClass: e.deviceClass,
                crowdSourcedLocation: null,
                role: null,
                lostModeMetadata: null,
              ))
          .toList();

      if (beaconCacheDate == null || DateTime.now().difference(beaconCacheDate!).inMinutes > 3) {
        isInClique.value = await PushSvc.checkClique();
        try {
          cachedBeacons.value = isInClique.value
              ? await api.getBeaconItems(items: PushSvc.state!.icloudServices!.fmfd!)
              : <api.DartBeacon>[];
        } catch (e, s) {
          Logger.error("Failed to fetch beacon data", error: e, trace: s);
        }
        beaconCacheDate = DateTime.now();

        for (final beacon in cachedBeacons) {
          if (beacon.lastReport == null) continue;
          try {
            final placemark = await PushSvc.reverseGeocode(beacon.lastReport!.lat, beacon.lastReport!.long);
            if (placemark == null) continue;
            cachedAddresses[(beacon.lastReport!.lat, beacon.lastReport!.long)] = Address(
              subAdministrativeArea: placemark.subAdministrativeArea,
              label: placemark.thoroughfare ?? placemark.name,
              streetAddress: placemark.thoroughfare,
              country: placemark.country,
              countryCode: placemark.isoCountryCode,
              administrativeArea: placemark.administrativeArea,
              streetName: placemark.thoroughfare,
              formattedAddressLines: [
                if (placemark.thoroughfare != null) placemark.thoroughfare!,
                if (placemark.locality != null) placemark.locality!,
                if (placemark.administrativeArea != null) placemark.administrativeArea!,
              ],
              mapItemFullAddress: null,
              fullThroroughfare: null,
              areaOfInterest: const [],
              locality: placemark.locality,
              stateCode: placemark.administrativeArea?.substring(0, 2).toUpperCase(),
            );
          } catch (e, s) {
            Logger.warn("Geocoding failed", error: e, trace: s);
          }
        }
      }

      for (final beacon in cachedBeacons) {
        final report = beacon.lastReport;
        final beaconLocation = report == null
            ? null
            : Location(
                positionType: null,
                verticalAccuracy: report.confidence,
                longitude: report.long,
                floorLevel: null,
                isInaccurate: null,
                isOld: null,
                horizontalAccuracy: report.horizontalAccuracy.toDouble(),
                latitude: report.lat,
                timeStamp: api.systemtimeToMillis(time: report.timestamp),
                altitude: null,
                locationFinished: true,
              );

        if (beacon.productId == -1) {
          final existingDevice =
              mappedDevices.firstWhereOrNull((d) => d.name == beacon.naming.name && !d.isConsideredAccessory);
          if (existingDevice == null || report == null) continue;
          if (existingDevice.location?.timeStamp != null &&
              api.systemtimeToMillis(time: report.timestamp) < existingDevice.location!.timeStamp!) {
            continue;
          }
          existingDevice.location = beaconLocation;
          continue;
        }

        mappedDevices.add(
          FindMyDevice(
            deviceModel: beacon.model,
            lowPowerMode: false,
            passcodeLength: null,
            itemGroup: null,
            id: beacon.id,
            batteryStatus: null,
            audioChannels: const [],
            lostModeCapable: true,
            snd: null,
            batteryLevel: beacon.batteryLevel?.toDouble(),
            locationEnabled: true,
            isConsideredAccessory: true,
            address: report == null ? null : cachedAddresses[(report.lat, report.long)],
            location: beaconLocation,
            modelDisplayName: null,
            deviceColor: null,
            activationLocked: false,
            rm2State: null,
            locFoundEnabled: false,
            nwd: null,
            deviceStatus: null,
            remoteWipe: null,
            fmlyShare: null,
            thisDevice: false,
            lostDevice: null,
            lostModeEnabled: false,
            deviceDisplayName: beacon.naming.name,
            safeLocations: const [],
            name: beacon.naming.name,
            canWipeAfterLock: false,
            isMac: false,
            rawDeviceModel: beacon.model,
            baUuid: null,
            trackingInfo: null,
            features: null,
            deviceDiscoveryId: null,
            prsId: null,
            scd: null,
            locationCapable: null,
            remoteLock: null,
            wipeInProgress: null,
            darkWake: null,
            deviceWithYou: false,
            maxMsgChar: null,
            deviceClass: null,
            crowdSourcedLocation: null,
            role: {
              "emoji": beacon.naming.emoji,
              "sharingId": beacon.shared?.shareId,
              "sharingActive": beacon.shared?.acceptanceState,
              "sharingName": beacon.shared?.ownerHandle != null
                  ? RustPushBBUtils.rustHandleToBB(beacon.shared!.ownerHandle).displayName
                  : null,
            },
            lostModeMetadata: null,
          ),
        );
      }

      devices.value = mappedDevices;

      for (final device in devices.where((e) => e.location?.latitude != null && e.location?.longitude != null)) {
        buildDeviceMarker(device);
      }
      fetching.value = false;
      refreshing.value = false;
    } catch (e, s) {
      Logger.error("Failed to parse FindMy Devices location data!", error: e, trace: s);
      fetching.value = null;
      refreshing.value = false;
      return;
    }

    if (refreshFriends) {
      canRefresh.value = false;
      _scheduleRefreshGate();
    }
  }

  void buildDeviceMarker(FindMyDevice e) {
    markers[e.id ?? randomString(6)] = Marker(
      key: ValueKey('device-${e.id ?? randomString(6)}'),
      point: LatLng(e.location!.latitude!, e.location!.longitude!),
      width: 30,
      height: 35,
      child: ClipShadowPath(
        clipper: const FindMyPinClipper(),
        shadow: const BoxShadow(
          color: Colors.black,
          blurRadius: 2,
        ),
        child: Container(
          color: Colors.white,
          child: Center(
            child: Padding(
              padding: const EdgeInsets.only(bottom: 5.0),
              child: e.role?['emoji'] != null
                  ? Text(
                      e.role!['emoji'],
                      style: Get.context!.theme.textTheme.bodyLarge!.copyWith(fontFamily: 'Apple Color Emoji'),
                    )
                  : Icon(
                      (e.isMac ?? false)
                          ? Icons.computer
                          : e.isConsideredAccessory
                              ? Icons.headphones
                              : Icons.phone_iphone,
                      color: Colors.black,
                      size: 20,
                    ),
            ),
          ),
        ),
      ),
      alignment: Alignment.topCenter,
    );
  }

  void buildFriendMarker(FindMyFriend friend) {
    final markerKey = friend.stableId ?? randomString(6);
    markers[markerKey] = Marker(
      key: ValueKey('friend-$markerKey'),
      point: LatLng(friend.latitude!, friend.longitude!),
      width: 35,
      height: 35,
      child: Container(
        decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(3),
            child: ContactAvatarWidget(
                editable: false, handle: friend.handle ?? Handle(address: friend.title ?? "Unknown")),
          ),
        ),
      ),
      alignment: Alignment.topCenter,
    );
  }

  void buildLocationMarker(Position pos) {
    markers['current'] = Marker(
      key: const ValueKey('current'),
      point: LatLng(pos.latitude, pos.longitude),
      width: 25,
      height: 55,
      child: Stack(
        alignment: Alignment.center,
        children: [
          if (pos.heading.isFinite)
            Transform.rotate(
              angle: pos.heading,
              child: ClipPath(
                clipper: const FindMyLocationClipper(),
                child: Container(
                  width: 25,
                  height: 55,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: AlignmentDirectional.center,
                      end: Alignment.topCenter,
                      colors: [
                        Get.context!.theme.colorScheme.primary,
                        Get.context!.theme.colorScheme.primary.withAlpha(50),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          Container(
            width: 25,
            height: 25,
            decoration: const BoxDecoration(
              shape: BoxShape.circle,
              color: Colors.white,
            ),
            padding: const EdgeInsets.all(5),
            child: Container(
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Get.context!.theme.colorScheme.primary,
              ),
            ),
          ),
        ],
      ),
      alignment: Alignment.topCenter,
    );
  }

  Future<void> deleteShared(FindMyDevice item) async {
    await api.deleteBeaconShare(items: PushSvc.state!.icloudServices!.fmfd!, share: item.role!['sharingId']!);
    devices.remove(item);
    beaconCacheDate = null;
  }

  Future<void> addShared(FindMyDevice item) async {
    await api.acceptBeaconShare(items: PushSvc.state!.icloudServices!.fmfd!, share: item.role!['sharingId']!);
    devices.remove(item);
    beaconCacheDate = null;
    await getLocations();
  }

  @override
  void onClose() {
    myTimer?.cancel();
    _refreshTimer?.cancel();
    locationSub?.cancel();
    mapController.dispose();
    popupController.dispose();
    tabController?.dispose();
    SocketSvc.socket?.off("new-findmy-location");
    itemsController.dispose();
    devicesController.dispose();
    friendsController.dispose();
    super.onClose();
  }
}
