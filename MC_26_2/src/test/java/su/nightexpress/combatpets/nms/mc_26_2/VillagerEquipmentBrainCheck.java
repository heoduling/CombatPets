package su.nightexpress.combatpets.nms.mc_26_2;

import java.nio.file.Files;
import java.nio.file.Path;

/** Guards the two lifecycle paths that can install vanilla villager behaviors. */
public final class VillagerEquipmentBrainCheck {

    private VillagerEquipmentBrainCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected repository root");

        Path sourcePath = Path.of(args[0]).resolve(
            "MC_26_2/src/main/java/su/nightexpress/combatpets/nms/mc_26_2/pets/animal/VillagerPet.java");
        String source = Files.readString(sourcePath).replace("\r\n", "\n");

        require(source, "super(EntityTypes.get(\"villager\"), level);\n        // Vanilla ShowTradesToPlayer clears MAINHAND");
        require(source, "public void refreshBrain(@NotNull ServerLevel level)");
        require(source, "super.refreshBrain(level);\n        this.getBrain().removeAllBehaviors();");
        require(source, "new PetFollowOwnerGoal(this)");
        require(source, "new PetMeleeAttackGoal(this)");

        System.out.println("VILLAGER_EQUIPMENT_BRAIN_CHECK=PASS");
    }

    private static void require(String source, String expected) {
        if (!source.contains(expected)) {
            throw new AssertionError("Missing required VillagerPet lifecycle guard: " + expected);
        }
    }
}
