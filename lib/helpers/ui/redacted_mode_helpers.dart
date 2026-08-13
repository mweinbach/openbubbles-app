import 'package:faker/faker.dart';

String generateFakeName() {
  return "${faker.person.firstName()} ${faker.person.lastName()}";
}
