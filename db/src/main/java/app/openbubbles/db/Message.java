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
public class Message {
    @Id
    public long id;
    public Long originalROWID;
    @Index(type = IndexType.VALUE)
    @Unique
    public String guid;
    public Long handleId;
    public Long otherHandle;
    public String text;
    public String subject;
    public String country;
    @Index(type = IndexType.VALUE)
    public Date dateCreated;
    public Date dateRead;
    public Date dateDelivered;
    public boolean isFromMe;
    public boolean hasDdResults;
    public Date datePlayed;
    public Long itemType;
    public String groupTitle;
    public Long groupActionType;
    public String balloonBundleId;
    public String associatedMessageGuid;
    public String associatedMessageType;
    public String expressiveSendStyleId;
    public boolean hasAttachments;
    public boolean hasReactions;
    public Date dateDeleted;
    public String threadOriginatorGuid;
    public String threadOriginatorPart;
    public boolean bigEmoji;
    public Long error;
    public final ToOne<Chat> chat = new ToOne<>(this, Message_.chat);
    public String dbAttributedBody;
    public Long associatedMessagePart;
    public boolean hasApplePayloadData;
    public Date dateEdited;
    public String dbMessageSummaryInfo;
    public String dbPayloadData;
    public String dbMetadata;
    public Boolean wasDeliveredQuietly;
    public Boolean didNotifyRecipient;
    public Boolean isBookmarked;
    public boolean hasBeenForwarded;
    public String stagingGuid;
    public String amkSessionId;
    public Boolean isDelivered;
    public Boolean verificationFailed;
    public String sendingServiceId;
    public String associatedMessageEmoji;
    public Date dateScheduled;
    public String ckRecordId;
    public Boolean ckSyncState;
    public final ToOne<Handle> handleRelation = new ToOne<>(this, Message_.handleRelation);
    public String errorMessage;
    public Boolean hasEffectPlayed;
    public Map<String, Object> metadata;
    @Backlink(to = "message")
    public final ToMany<Attachment> dbAttachments = new ToMany<>(this, Message_.dbAttachments);
    // Normally added by the ObjectBox bytecode transformer; explicit here
    // (see objectbox-java relation tests for the same pattern).
    transient BoxStore __boxStore;
}
