// GENERATED from lib/generated/objectbox-model.json by
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

@Entity
public class ContactV2 {
    @Id
    public long id;
    public String displayName;
    @Unique
    public String nativeContactId;
    public String avatarPath;
    public List<String> addresses = new ArrayList<>();
    public String nickname;
    public String firstName;
    public String lastName;
    public String middleName;
    public String namePrefix;
    public String nameSuffix;
    public String company;
    public String dbPhoneNumbers;
    public String dbEmailAddresses;
    public boolean isNative;
    public String posterPath;
    public final ToMany<Handle> handles = new ToMany<>(this, ContactV2_.handles);
    // Normally added by the ObjectBox bytecode transformer; explicit here
    // (see objectbox-java relation tests for the same pattern).
    transient BoxStore __boxStore;
}
