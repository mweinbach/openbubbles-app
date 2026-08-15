#!/usr/bin/env python3
"""Generate Java ObjectBox entities from the Flutter app's objectbox-model.json.

Java (not Kotlin) on purpose: kotlin-kapt's stub rounds cannot reference the
generated X_ classes from entity source, so Kotlin entities must rely on the
gradle plugin's bytecode transformer ("initialization magic") — which does
not run reliably for plain kotlin-jvm modules. In Java, the explicit pattern
from ObjectBox's own tests works everywhere:

    public final ToOne<Order> customer = new ToOne<>(this, Order_.customer);
    @Backlink(to = "customer") public final ToMany<Order> orders = new ToMany<>(this, Customer_.orders);
    transient BoxStore __boxStore;  // normally injected by the transformer

UID parity: identical property names + types => the plugin keeps the seeded
UIDs in db/objectbox-model.json => the same store files open. Guard: that
file must stay byte-identical to lib/generated/objectbox-model.json.

Regenerate after changing the seed model:
    python3 tools/gen_db_entities.py
"""
import json
import os
import sys

MODEL = "db/seed-objectbox-model.json"
OUT_DIR = "db/src/main/java/app/openbubbles/db"

# model property name -> (java field name, target entity)
TO_ONE_FIELDS = {
    "messageId": ("message", "Message"),
    "chatId": ("chat", "Chat"),
    "handleRelationId": ("handleRelation", "Handle"),
    "dbLatestMessageId": ("dbLatestMessage", "Message"),
    "themeObjectId": ("themeObject", "ThemeObject"),
}

# entity -> [(field, backlink-to, target)]
BACKLINKS = {
    "Chat": [("messages", "chat", "Message")],
    "ThemeObject": [("themeEntries", "themeObject", "ThemeEntry")],
    "Handle": [("contactsV2", "handles", "ContactV2")],
    "Message": [("dbAttachments", "message", "Attachment")],
}

JAVA_TYPE = {
    "1": ("boolean", "Boolean"),
    "6": ("long", "Long"),
    "9": ("String", "String"),
    "10": ("Date", "Date"),
    "13": ("Map<String, Object>", "Map<String, Object>"),  # flex
    "23": ("byte[]", "byte[]"),                            # byteVector
    "30": ("List<String>", "List<String>"),                # stringVector
}

# primitives for these (non-null with defaults in the Dart entities)
NON_NULL_PROPS = {
    "Attachment": {"isOutgoing", "isDownloaded", "hasLivePhoto"},
    "Chat": {"isArchived", "isPinned", "hasUnreadMessage", "autoSendReadReceipts",
             "autoSendTypingIndicators", "shareZenMode", "notifsSilenced",
             "senderIsKnown", "isRoutingStub"},
    "Handle": {"service", "uniqueAddressAndService"},
    "ThemeEntry": {"isFont"},
    "Message": {"isFromMe", "hasDdResults", "hasAttachments", "hasReactions",
                "bigEmoji", "hasApplePayloadData", "hasBeenForwarded"},
    "ContactV2": {"isNative"},
}

HEADER = """// GENERATED from lib/generated/objectbox-model.json by
// tools/gen_db_entities.py — do not edit by hand; regenerate instead.
package app.openbubbles.db;

import io.objectbox.BoxStore;
import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.annotation.IndexType;
import io.objectbox.annotation.Unique;
import io.objectbox.relation.ToMany;
import io.objectbox.relation.ToOne;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
"""


def main():
    model = json.load(open(MODEL))
    os.makedirs(OUT_DIR, exist_ok=True)
    for ent in model["entities"]:
        name = ent["name"]
        out = [HEADER, f"@Entity\npublic class {name} {{"]
        for prop in ent["properties"]:
            pname = prop["name"]
            ptype = str(prop["type"])
            flags = prop.get("flags", 0)

            if ptype == "11":
                field, target = TO_ONE_FIELDS[pname]
                out.append(f"    public final ToOne<{target}> {field} = new ToOne<>(this, {name}_.{field});")
                continue

            if pname == "id":
                out.append("    @Id")
                out.append("    public long id;")
                continue

            anns = []
            if flags & 8:
                # Dart's @Index() default is a VALUE index (flag 8); the Java
                # annotation defaults to HASH (2048) on strings.
                anns.append("@Index(type = IndexType.VALUE)")
            if flags & 32:
                anns.append("@Unique")
            for a in anns:
                out.append(f"    {a}")

            prim, boxed = JAVA_TYPE[ptype]
            if pname in NON_NULL_PROPS.get(name, set()) and prim != boxed:
                decl = f"public {prim} {pname};"
            elif ptype == "30":
                decl = f"public {boxed} {pname} = new ArrayList<>();"
            elif ptype == "23":
                decl = f"public {boxed} {pname};"
            else:
                decl = f"public {boxed} {pname};"
            out.append(f"    {decl}")

        for rel in ent.get("relations", []):
            out.append(f"    public final ToMany<{target_name(model, rel['targetId'])}> {rel['name']}"
                       f" = new ToMany<>(this, {name}_.{rel['name']});")

        for field, to, target in BACKLINKS.get(name, []):
            out.append(f"    @Backlink(to = \"{to}\")")
            out.append(f"    public final ToMany<{target}> {field} = new ToMany<>(this, {name}_.{field});")

        out.append("    // Normally added by the ObjectBox bytecode transformer; explicit here")
        out.append("    // (see objectbox-java relation tests for the same pattern).")
        out.append("    transient BoxStore __boxStore;")
        out.append("}")
        path = os.path.join(OUT_DIR, f"{name}.java")
        open(path, "w").write("\n".join(out) + "\n")
        print(f"wrote {path}")


def target_name(model, target_id):
    uid = target_id.split(":")[1]
    for ent in model["entities"]:
        if ent["id"].split(":")[1] == uid:
            return ent["name"]
    raise KeyError(target_id)


if __name__ == "__main__":
    sys.exit(main())
