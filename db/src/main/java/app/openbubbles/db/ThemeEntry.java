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
public class ThemeEntry {
    @Id
    public long id;
    public Long themeId;
    public String name;
    public boolean isFont;
    public Long fontSize;
    public String dbColor;
    public Long fontWeight;
    public final ToOne<ThemeObject> themeObject = new ToOne<>(this, ThemeEntry_.themeObject);
    // Normally added by the ObjectBox bytecode transformer; explicit here
    // (see objectbox-java relation tests for the same pattern).
    transient BoxStore __boxStore;
}
