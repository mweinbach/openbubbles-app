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
public class Attachment {
    @Id
    public long id;
    public Long originalROWID;
    @Index(type = IndexType.VALUE)
    @Unique
    public String guid;
    public String uti;
    public String mimeType;
    public boolean isOutgoing;
    public String transferName;
    public Long totalBytes;
    public Long height;
    public Long width;
    public String webUrl;
    public final ToOne<Message> message = new ToOne<>(this, Attachment_.message);
    public String dbMetadata;
    public boolean hasLivePhoto;
    public String ckRecordId;
    public boolean isDownloaded;
    public Map<String, Object> metadata;
    public Map<String, Object> exif;
    // Normally added by the ObjectBox bytecode transformer; explicit here
    // (see objectbox-java relation tests for the same pattern).
    transient BoxStore __boxStore;
}
