package su.nightexpress.combatpets.pet;

import su.nightexpress.combatpets.api.pet.ActivePet;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class PetManagerLifecycleQueueCheck {

    private PetManagerLifecycleQueueCheck() {
    }

    public static void main(String[] args) {
        ActivePet active = pet("active");
        ActivePet retired = pet("retired");
        Queue<ActivePet> queue = new ConcurrentLinkedQueue<>();
        queue.offer(active);
        queue.offer(retired);

        List<ActivePet> result = PetManager.collectLifecyclePets(List.of(active), queue);
        if (!result.equals(List.of(active, retired))) {
            throw new AssertionError("lifecycle snapshot did not include and de-duplicate retired pets: " + result);
        }
        if (!queue.isEmpty()) {
            throw new AssertionError("retired cleanup queue was not drained");
        }
    }

    private static ActivePet pet(String name) {
        return (ActivePet) Proxy.newProxyInstance(
            ActivePet.class.getClassLoader(),
            new Class<?>[]{ActivePet.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> name;
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}
