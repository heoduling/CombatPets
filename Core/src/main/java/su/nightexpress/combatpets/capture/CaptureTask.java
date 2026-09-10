package su.nightexpress.combatpets.capture;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.Placeholders;
import su.nightexpress.combatpets.api.pet.Template;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.combatpets.api.pet.event.capture.PetCaptureFailureEvent;
import su.nightexpress.combatpets.api.pet.event.capture.PetCaptureSuccessEvent;
import su.nightexpress.combatpets.api.pet.event.capture.PetEscapeCaptureEvent;
import su.nightexpress.combatpets.capture.config.CaptureConfig;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.config.Lang;
import su.nightexpress.combatpets.util.PetScheduler;
import su.nightexpress.nightcore.util.random.Rnd;
import su.nightexpress.nightcore.util.wrapper.UniParticle;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class CaptureTask {

    private static final int END_COUNTER = 100;
    private static final int ADD_COUNT = 8;

    private final PetsPlugin plugin;
    private final CaptureManager manager;
    private final Player player;
    private final LivingEntity entity;
    private final Template template;
    private final Tier tier;
    private final boolean shouldWin;
    private final UUID playerId;

    private int success;
    private int failure;
    private volatile boolean running;

    public CaptureTask(@NotNull PetsPlugin plugin,
                       @NotNull Player player,
                       @NotNull LivingEntity entity,
                       @NotNull Template template,
                       @NotNull Tier tier) {
        this(plugin, plugin.getCaptureManager(), player, entity, template, tier);
    }

    CaptureTask(@NotNull PetsPlugin plugin,
                @NotNull CaptureManager manager,
                @NotNull Player player,
                @NotNull LivingEntity entity,
                @NotNull Template template,
                @NotNull Tier tier) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.entity = entity;
        this.template = template;
        this.tier = tier;
        this.playerId = player.getUniqueId();
        this.success = 0;
        this.failure = 0;
        this.shouldWin = Rnd.chance(this.template.getCaptureChance());
        this.running = false;
    }

    public boolean isRunning() {
        return this.running;
    }

    /** Starts the task on the target entity's owning region. */
    public boolean start() {
        if (this.running) return false;

        this.running = true;
        try {
            boolean scheduled = PetScheduler.runAtEntity(this.plugin, this.entity,
                this::startAtEntity, this::onEntityRetired);
            if (!scheduled) this.onEntityRetired();
            return scheduled;
        }
        catch (IllegalPluginAccessException exception) {
            this.running = false;
            if (this.plugin.isEnabled()) throw exception;
            this.manager.removeTask(this);
            return false;
        }
    }

    private void startAtEntity() {
        if (!this.running || !this.plugin.isEnabled()) {
            this.onEntityRetired();
            return;
        }

        if (this.entity.isDead() || !this.entity.isValid()) {
            this.stopAtEntity();
            return;
        }

        if (this.abortForbiddenCaptureState()) return;

        this.success = CaptureManager.getSavedCaptureProgress(this.entity);
        this.entity.setAI(false);
        this.scheduleNextTick();
    }

    /** Called only from the target entity's owning region. */
    private void tickAtEntity() {
        if (!this.running || !this.plugin.isEnabled()) return;

        if (this.abortForbiddenCaptureState()) return;

        if (this.success >= END_COUNTER || this.failure >= END_COUNTER) {
            if (this.success > this.failure) this.onSuccess();
            else this.onFailure();
            return;
        }

        if (this.entity.isDead() || !this.entity.isValid()) {
            this.stopAtEntity();
            return;
        }

        Location location = this.entity.getLocation();
        EntitySnapshot snapshot = new EntitySnapshot(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ());

        try {
            PetScheduler.runAtEntity(this.plugin, this.player,
                () -> this.checkPlayer(snapshot), this::onPlayerRetired);
        }
        catch (IllegalPluginAccessException exception) {
            if (this.plugin.isEnabled()) throw exception;
            this.onEntityRetired();
        }
    }

    /** Called only from the target entity's owning region. */
    private boolean abortForbiddenCaptureState() {
        if (!CaptureManager.hasForbiddenCaptureState(this.entity)) return false;

        this.stopAtEntity();
        this.sendToPlayer(player -> Lang.CAPTURE_ERROR_NOT_CAPTURABLE.message().send(player, replacer -> replacer
            .replace(Placeholders.GENERIC_NAME, this.template.getDefaultName())));
        return true;
    }

    /** Called only from the player's owning region. */
    private void checkPlayer(@NotNull EntitySnapshot entitySnapshot) {
        if (!this.running || !this.plugin.isEnabled()) return;

        if (!this.player.isOnline() || this.player.isDead() || !this.player.getWorld().getUID().equals(entitySnapshot.worldId())) {
            this.requestStop();
            return;
        }

        Location playerLocation = this.player.getLocation();
        double distanceSquared = entitySnapshot.distanceSquared(playerLocation);
        double maxDistance = Math.max(0D, CaptureConfig.CAPTURE_MAX_DISTANCE.get());
        if (distanceSquared > maxDistance * maxDistance) {
            Lang.CAPTURE_FAIL_DISTANCE.message().send(this.player);
            this.requestStop();
            return;
        }

        try {
            PetScheduler.runAtEntity(this.plugin, this.entity, this::continueAtEntity, this::onEntityRetired);
        }
        catch (IllegalPluginAccessException exception) {
            if (this.plugin.isEnabled()) throw exception;
            this.onPlayerRetired();
        }
    }

    /** Called only from the target entity's owning region. */
    private void continueAtEntity() {
        if (!this.running || !this.plugin.isEnabled()) return;

        if (this.entity.isDead() || !this.entity.isValid()) {
            this.stopAtEntity();
            return;
        }

        if (this.tryEscape()) {
            this.stopAtEntity();
            return;
        }

        if (!this.entity.isLeashed()) this.entity.setLeashHolder(this.player);

        Location location = this.entity.getLocation();
        location.setYaw(Rnd.get(360));
        this.entity.teleportAsync(location);

        boolean random = this.resultPredefined()
            ? Rnd.nextBoolean() == this.shouldWin
            : Rnd.chance(this.template.getCaptureChance());

        if (random) this.countSuccess();
        else this.countFailure();

        UniParticle.of(Particle.SMOKE).play(this.entity.getLocation(), 0.5, 0.1, 10);
        this.sendToPlayer(player -> Lang.CAPTURE_PROGRESS.message().send(player, replacer -> replacer
            .replace(Placeholders.GENERIC_SUCCESS, String.valueOf(this.success))
            .replace(Placeholders.GENERIC_FAILURE, String.valueOf(this.failure))));

        this.scheduleNextTick();
    }

    private void scheduleNextTick() {
        if (!this.running || !this.plugin.isEnabled()) return;

        try {
            boolean scheduled = PetScheduler.runAtEntityLater(this.plugin, this.entity,
                this::tickAtEntity, this::onEntityRetired, 1L);
            if (!scheduled) this.onEntityRetired();
        }
        catch (IllegalPluginAccessException exception) {
            if (this.plugin.isEnabled()) throw exception;
            this.onEntityRetired();
        }
    }

    private boolean resultPredefined() {
        return !CaptureConfig.CAPTURE_SAVE_PROGRESS.get();
    }

    private void countSuccess() {
        this.success = Math.min(END_COUNTER, this.success + Rnd.get(ADD_COUNT));
        if (this.success == END_COUNTER && this.resultPredefined() && !this.shouldWin) this.success -= 1;
    }

    private void countFailure() {
        this.failure = Math.min(END_COUNTER, this.failure + Rnd.get(ADD_COUNT));
        if (this.failure == END_COUNTER && this.resultPredefined() && this.shouldWin) this.failure -= 1;
    }

    /** Called only from the target entity's owning region. */
    private void onSuccess() {
        Location location = this.entity.getLocation().clone();
        double height = this.entity.getHeight();
        ItemStack itemStack = this.template.createEgg(this.tier);
        if (Config.isWardrobeEnabled()) {
            this.plugin.getWardrobeManager().storeAccessoryData(this.entity, itemStack);
            this.plugin.getItemManager().refreshEggRecovery(itemStack);
        }

        this.stopAtEntity();
        this.entity.getWorld().dropItemNaturally(location, itemStack);
        this.entity.remove();

        UniParticle.of(Particle.CLOUD).play(location, 0.5, 0.1, 30);
        UniParticle.of(Particle.HEART).play(location.clone().add(0D, height, 0D), 0.05, 1);

        PetCaptureSuccessEvent event = new PetCaptureSuccessEvent(this.player, this.entity, this.template, this.tier);
        this.plugin.getPluginManager().callEvent(event);

        this.sendToPlayer(player -> Lang.CAPTURE_SUCCESS.message().send(player, replacer -> replacer
            .replace(Placeholders.TEMPLATE_DEFAULT_NAME, this.template.getDefaultName())
            .replace(Placeholders.TIER_NAME, this.tier.getName())));
    }

    /** Called only from the target entity's owning region. */
    private void onFailure() {
        Location eyeLocation = this.entity.getEyeLocation().clone();
        if (CaptureConfig.CAPTURE_SAVE_PROGRESS.get()) CaptureManager.saveCaptureProgress(this.entity, this.success);

        UniParticle.of(Particle.LAVA).play(eyeLocation, 0.1, 15);
        PetCaptureFailureEvent event = new PetCaptureFailureEvent(this.player, this.entity, this.template, this.tier);
        this.plugin.getPluginManager().callEvent(event);

        this.stopAtEntity();
        this.sendToPlayer(player -> Lang.CAPTURE_FAIL_UNLUCK.message().send(player, replacer -> replacer
            .replace(Placeholders.TEMPLATE_DEFAULT_NAME, this.template.getDefaultName())
            .replace(Placeholders.TIER_NAME, this.tier.getName())));
    }

    /** Called only from the target entity's owning region. */
    private boolean tryEscape() {
        if (!CaptureConfig.CAPTURE_ESCAPE_ALLOWED.get()) return false;
        if (!Rnd.chance(this.template.getCaptureEscapeChance())) return false;

        this.entity.damage(0.1D, this.player);
        UniParticle.of(Particle.ANGRY_VILLAGER).play(this.entity.getEyeLocation(), 0.1, 3);

        PetEscapeCaptureEvent event = new PetEscapeCaptureEvent(this.player, this.entity, this.template, this.tier);
        this.plugin.getPluginManager().callEvent(event);
        this.sendToPlayer(player -> Lang.CAPTURE_FAIL_ESCAPED.message().send(player, replacer -> replacer
            .replace(Placeholders.TEMPLATE_DEFAULT_NAME, this.template.getDefaultName())
            .replace(Placeholders.TIER_NAME, this.tier.getName())));
        return true;
    }

    /** Requests entity cleanup from any thread without reading entity state there. */
    public void stop() {
        this.stop(() -> {});
    }

    public void stop(@NotNull Runnable completion) {
        AtomicBoolean completed = new AtomicBoolean();
        Runnable finish = () -> {
            if (completed.compareAndSet(false, true)) completion.run();
        };

        if (!this.running) {
            this.manager.removeTask(this);
            finish.run();
            return;
        }

        this.running = false;
        this.manager.removeTask(this);
        try {
            boolean scheduled = PetScheduler.runAtEntity(this.plugin, this.entity,
                () -> {
                    try {
                        this.clearEntityAtEntity();
                    }
                    finally {
                        finish.run();
                    }
                }, () -> {
                    this.onEntityRetired();
                    finish.run();
                });
            if (!scheduled) finish.run();
        }
        catch (IllegalPluginAccessException exception) {
            if (this.plugin.isEnabled()) {
                finish.run();
                throw exception;
            }
            this.onEntityRetired();
            finish.run();
        }
    }

    private void requestStop() {
        this.stop();
    }

    /** Called only from the target entity's owning region. */
    private void stopAtEntity() {
        this.clearEntityAtEntity();
    }

    /** Called only from the target entity's owning region. */
    private void clearEntityAtEntity() {
        if (this.entity.isValid() && !this.entity.isDead()) {
            this.entity.setLeashHolder(null);
            this.entity.setAI(true);
        }
        this.running = false;
        this.manager.removeTask(this);
    }

    private void onEntityRetired() {
        this.running = false;
        this.manager.removeTask(this);
    }

    private void onPlayerRetired() {
        // EntityScheduler retired callbacks run in critical scheduler code.
        // Do not schedule another entity from here.
        this.running = false;
        this.manager.removeTask(this);
    }

    private void sendToPlayer(@NotNull Consumer<Player> action) {
        if (!this.plugin.isEnabled()) return;
        try {
            PetScheduler.runAtEntity(this.plugin, this.player, () -> {
                if (this.plugin.isEnabled() && this.player.isOnline()) action.accept(this.player);
            });
        }
        catch (IllegalPluginAccessException exception) {
            if (this.plugin.isEnabled()) throw exception;
        }
    }

    @NotNull
    public LivingEntity getEntity() {
        return this.entity;
    }

    @NotNull
    UUID getPlayerId() {
        return this.playerId;
    }

    private record EntitySnapshot(@NotNull UUID worldId, double x, double y, double z) {
        private double distanceSquared(@NotNull Location location) {
            double dx = this.x - location.getX();
            double dy = this.y - location.getY();
            double dz = this.z - location.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
