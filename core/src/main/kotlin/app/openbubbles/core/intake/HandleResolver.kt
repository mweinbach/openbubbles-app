package app.openbubbles.core.intake

import app.openbubbles.core.model.MessageMapper
import app.openbubbles.db.Handle
import app.openbubbles.db.Handle_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder

/**
 * Handle normalization shared by the ingestor and the repos — the
 * `RustPushBBUtils.rustHandleToBB` port: find by (address, service), create
 * when missing, and make sure originalROWID is populated (the Flutter app
 * mirrors the row id into it; group-event rows reference it via
 * Message.otherHandle).
 */
object HandleResolver {

    fun resolve(store: BoxStore, rustHandle: String, service: String): Handle {
        val handleBox = store.boxFor(Handle::class.java)
        val address = MessageMapper.normalizeAddress(rustHandle)
        val uniqueKey = "$address/$service"
        // Native-created rows use this unique indexed composite. Retain the
        // address/service fallback for Flutter-era stores whose composite was
        // encoded differently.
        handleBox.query()
            .equal(
                Handle_.uniqueAddressAndService,
                uniqueKey,
                QueryBuilder.StringOrder.CASE_SENSITIVE,
            )
            .build().use { it.findFirst() }?.let { return it }
        handleBox.query()
            .equal(Handle_.address, address, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .equal(Handle_.service, service, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }?.let { return it }

        val handle = Handle().apply {
            this.address = address
            this.service = service
            uniqueAddressAndService = uniqueKey
        }
        handleBox.put(handle)
        if (handle.originalROWID == null) {
            handle.originalROWID = handle.id
            handleBox.put(handle)
        }
        return handle
    }
}
