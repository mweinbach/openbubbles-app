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
public class Handle {
    @Id
    public long id;
    public Long originalROWID;
    public String address;
    public String country;
    public String color;
    public String defaultPhone;
    public String defaultEmail;
    public String formattedAddress;
    @Unique
    public String uniqueAddressAndService;
    public String service;
    public String posterPath;
    public Boolean blocked;
    @Backlink(to = "handles")
    public final ToMany<ContactV2> contactsV2 = new ToMany<>(this, Handle_.contactsV2);
    // Normally added by the ObjectBox bytecode transformer; explicit here
    // (see objectbox-java relation tests for the same pattern).
    transient BoxStore __boxStore;
}
