package model.system;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Maps a system type id to a factory. To add a system, write its class and add one
 * register(...) line below; the ship JSON then refers to it by id().
 */
public final class SystemRegistry {
    private static final Map<String, Supplier<ShipSystem>> FACTORIES = new LinkedHashMap<>();

    static {
        register(HelmSystem::new);
        register(EngineSystem::new);
        register(BatterySystem::new);
        register(AtmosphericsSystem::new);
        register(GasStorageSystem::new);
    }

    private SystemRegistry() {
    }

    public static void register(Supplier<ShipSystem> factory) {
        // build a throwaway instance just to read its id
        FACTORIES.put(factory.get().id(), factory);
    }

    public static ShipSystem create(String id) {
        Supplier<ShipSystem> factory = FACTORIES.get(id);
        return factory == null ? null : factory.get();
    }

    public static boolean exists(String id) {
        return FACTORIES.containsKey(id);
    }
}
