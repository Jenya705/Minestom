package net.minestom.server.entity;

import net.minestom.server.entity.metadata.item.ItemEntityMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityItemMergeEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.StackingRule;
import net.minestom.server.utils.Position;
import net.minestom.server.utils.time.Cooldown;
import net.minestom.server.utils.time.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.Set;

/**
 * Represents an item on the ground.
 */
public class ItemEntity extends Entity {

    /**
     * Used to slow down the merge check delay
     */
    private static Duration mergeDelay = Duration.of(10, TimeUnit.SERVER_TICK);

    /**
     * The last time that this item has checked his neighbors for merge
     */
    private long lastMergeCheck;

    private ItemStack itemStack;

    private boolean pickable = true;
    private boolean mergeable = true;
    private float mergeRange = 1;

    private long spawnTime;
    private long pickupDelay;

    public ItemEntity(@NotNull ItemStack itemStack, @NotNull Position spawnPosition) {
        super(EntityType.ITEM, spawnPosition);
        setItemStack(itemStack);
        setBoundingBox(0.25f, 0.25f, 0.25f);
    }

    public ItemEntity(@NotNull ItemStack itemStack, @NotNull Position spawnPosition, @Nullable Instance instance) {
        this(itemStack, spawnPosition);

        if (instance != null) {
            setInstance(instance);
        }
    }

    /**
     * Gets the update option for the merging feature.
     *
     * @return the merge update option
     */
    @Nullable
    public static Duration getMergeDelay() {
        return mergeDelay;
    }

    /**
     * Changes the merge update option.
     * Can be set to null to entirely remove the delay.
     *
     * @param mergeUpdateOption the new merge update option
     *
     * @deprecated Replaced by {@link #setMergeDelay(Duration)}
     */
    @SuppressWarnings("removal")
    public static void setMergeUpdateOption(@Nullable net.minestom.server.utils.time.UpdateOption mergeUpdateOption) {
        setMergeDelay(mergeUpdateOption == null ? null : mergeUpdateOption.toDuration());
    }

    /**
     * Changes the merge delay.
     * Can be set to null to entirely remove the delay.
     *
     * @param delay the new merge delay
     */
    public static void setMergeDelay(@Nullable Duration delay) {
        ItemEntity.mergeDelay = delay;
    }

    @Override
    public void update(long time) {
        if (isMergeable() && isPickable() &&
                (mergeDelay == null || !Cooldown.hasCooldown(time, lastMergeCheck, mergeDelay))) {
            this.lastMergeCheck = time;

            final Chunk chunk = instance.getChunkAt(getPosition());
            final Set<Entity> entities = instance.getChunkEntities(chunk);
            for (Entity entity : entities) {
                if (entity instanceof ItemEntity) {

                    // Do not merge with itself
                    if (entity == this)
                        continue;

                    final ItemEntity itemEntity = (ItemEntity) entity;
                    if (!itemEntity.isPickable() || !itemEntity.isMergeable())
                        continue;

                    // Too far, do not merge
                    if (getDistance(itemEntity) > mergeRange)
                        continue;

                    final ItemStack itemStackEntity = itemEntity.getItemStack();

                    final StackingRule stackingRule = itemStack.getStackingRule();
                    final boolean canStack = stackingRule.canBeStacked(itemStack, itemStackEntity);

                    if (!canStack)
                        continue;

                    final int totalAmount = stackingRule.getAmount(itemStack) + stackingRule.getAmount(itemStackEntity);
                    final boolean canApply = stackingRule.canApply(itemStack, totalAmount);

                    if (!canApply)
                        continue;

                    final ItemStack result = stackingRule.apply(itemStack, totalAmount);

                    EntityItemMergeEvent entityItemMergeEvent = new EntityItemMergeEvent(this, itemEntity, result);
                    EventDispatcher.callCancellable(entityItemMergeEvent, () -> {
                        setItemStack(entityItemMergeEvent.getResult());
                        itemEntity.remove();
                    });

                }
            }
        }
    }

    @Override
    public void spawn() {
        this.spawnTime = System.currentTimeMillis();
    }

    @Override
    public @NotNull ItemEntityMeta getEntityMeta() {
        return (ItemEntityMeta) super.getEntityMeta();
    }

    /**
     * Gets the item stack on ground.
     *
     * @return the item stack
     */
    @NotNull
    public ItemStack getItemStack() {
        return itemStack;
    }

    /**
     * Changes the item stack on ground.
     *
     * @param itemStack the item stack
     */
    public void setItemStack(@NotNull ItemStack itemStack) {
        this.itemStack = itemStack;
        getEntityMeta().setItem(itemStack);
    }

    /**
     * Gets if the item is currently pickable.
     * <p>
     * {@link #setPickable(boolean)} needs to be true and the delay {@link #getPickupDelay()}
     * to be long gone.
     *
     * @return true if the item is pickable, false otherwise
     */
    public boolean isPickable() {
        return pickable && (System.currentTimeMillis() - getSpawnTime() >= pickupDelay);
    }

    /**
     * Makes the item pickable.
     *
     * @param pickable true to make the item pickable, false otherwise
     */
    public void setPickable(boolean pickable) {
        this.pickable = pickable;
    }

    /**
     * Gets if the item is mergeable.
     *
     * @return true if the entity is mergeable, false otherwise
     */
    public boolean isMergeable() {
        return mergeable;
    }

    /**
     * When set to true, close {@link ItemEntity} will try to merge together as a single entity
     * when their {@link #getItemStack()} is similar and allowed to stack together.
     *
     * @param mergeable should the entity merge with other {@link ItemEntity}
     */
    public void setMergeable(boolean mergeable) {
        this.mergeable = mergeable;
    }

    /**
     * Gets the merge range.
     *
     * @return the merge range
     */
    public float getMergeRange() {
        return mergeRange;
    }

    /**
     * Changes the merge range.
     *
     * @param mergeRange the merge range
     */
    public void setMergeRange(float mergeRange) {
        this.mergeRange = mergeRange;
    }

    /**
     * Gets the pickup delay in milliseconds, defined by {@link #setPickupDelay(long, TemporalUnit)}.
     *
     * @return the pickup delay
     */
    public long getPickupDelay() {
        return pickupDelay;
    }

    /**
     * Sets the pickup delay of the ItemEntity.
     *
     * @param delay    the pickup delay
     * @param temporalUnit the unit of the delay
     */
    public void setPickupDelay(long delay, @NotNull TemporalUnit temporalUnit) {
        this.pickupDelay = TimeUnit.getMillis(delay, temporalUnit);
    }

    /**
     * Used to know if the ItemEntity can be pickup.
     *
     * @return the time in milliseconds since this entity has spawn
     */
    public long getSpawnTime() {
        return spawnTime;
    }
}
