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
public class Chat {
    @Id
    public long id;
    @Index(type = IndexType.VALUE)
    @Unique
    public String guid;
    public String chatIdentifier;
    public boolean isArchived;
    public String muteType;
    public String muteArgs;
    public boolean isPinned;
    public boolean hasUnreadMessage;
    public Date dbOnlyLatestMessageDate;
    public String displayName;
    public String customAvatarPath;
    public Long pinIndex;
    public boolean autoSendReadReceipts;
    public boolean autoSendTypingIndicators;
    public String textFieldText;
    public List<String> textFieldAttachments = new ArrayList<>();
    public Date dateDeleted;
    public Long style;
    public Boolean lockChatName;
    public Boolean lockChatIcon;
    public String lastReadMessageGuid;
    public Long groupVersion;
    public String usingHandle;
    public String apnTitle;
    public Boolean isRpSms;
    public List<String> guidRefs = new ArrayList<>();
    public Long telephonyId;
    public String textFieldAnnotations;
    public boolean shareZenMode;
    public boolean notifsSilenced;
    public Date dateNotifiedAnyways;
    public Long zenModeIsShared;
    public boolean senderIsKnown;
    public boolean isRoutingStub;
    public String transcriptPosterPath;
    public Long transcriptBackgroundVersion;
    public byte[] cloudData;
    public String ckRecordId;
    public Boolean ckSyncState;
    public String photoAttachmentGuid;
    public String cloudGuid;
    public final ToOne<Message> dbLatestMessage = new ToOne<>(this, Chat_.dbLatestMessage);
    public String customBackgroundPath;
    public String title;
    public String customThemeLight;
    public String customThemeDark;
    public String senderOverride;
    public final ToMany<Handle> handles = new ToMany<>(this, Chat_.handles);
    @Backlink(to = "chat")
    public final ToMany<Message> messages = new ToMany<>(this, Chat_.messages);
    // Normally added by the ObjectBox bytecode transformer; explicit here
    // (see objectbox-java relation tests for the same pattern).
    transient BoxStore __boxStore;
}
