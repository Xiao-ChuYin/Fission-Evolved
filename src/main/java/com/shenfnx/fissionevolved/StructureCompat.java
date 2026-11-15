package com.shenfnx.fissionevolved;

import java.lang.reflect.Method;
import java.util.List;

import gregtech.api.structure.error.StructureError;

/**
 * Bridges the structure-error API rename between GT5U generations.
 *
 * <p>
 * Newer GT5U (GTNH 2.9.0-beta-2 / daily, 5.09.54.98+) exposes
 * {@code checkHasAnyDynamo(List<StructureError>)} on the multi-block base. GTNH 2.9.0 beta-1 (5.09.52.604) does
 * not; its closest-looking method {@code checkHasAnyEnergy} checks energy-input hatches, which is semantically
 * wrong for a generator. The equivalent check is reproduced reflectively with {@code StructureErrors.hatchCount}
 * so no version-sensitive GT symbol is linked at compile time.
 * </p>
 */
final class StructureCompat {

    private StructureCompat() {}

    /**
     * Two-argument overload used by the binary patch, whose call site has exactly {@code (receiver, errors)} on the
     * stack. The dynamo-hatch state is recovered reflectively so no extra argument is needed.
     */
    static void checkHasAnyDynamo(Object controller, List<StructureError> errors) {
        checkHasAnyDynamo(controller, errors, hasAnyDynamo(controller));
    }

    private static boolean hasAnyDynamo(Object controller) {
        try {
            java.lang.reflect.Field normal = findField(controller.getClass(), "mDynamoHatches");
            if (normal != null) {
                normal.setAccessible(true);
                Object list = normal.get(controller);
                if (list instanceof java.util.Collection && !((java.util.Collection<?>) list).isEmpty()) {
                    return true;
                }
            }
            java.lang.reflect.Method exotic = controller.getClass()
                .getMethod("getExoticDynamoHatches");
            Object exoticList = exotic.invoke(controller);
            if (exoticList instanceof java.util.Collection) {
                return !((java.util.Collection<?>) exoticList).isEmpty();
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            // Unknown state: do not add a spurious structure error.
            return true;
        }
    }

    private static java.lang.reflect.Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking
            }
        }
        return null;
    }

    static void checkHasAnyDynamo(Object controller, List<StructureError> errors, boolean hasAnyDynamo) {
        Method method = findDynamoCheck(controller.getClass());
        if (method != null) {
            try {
                method.setAccessible(true);
                method.invoke(controller, errors);
                return;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return;
            }
        }
        if (hasAnyDynamo) {
            return;
        }
        addMissingDynamoError(errors);
    }

    private static Method findDynamoCheck(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod("checkHasAnyDynamo", List.class);
            } catch (NoSuchMethodException ignored) {
                // keep walking: beta-1 simply does not declare it
            }
        }
        return null;
    }

    private static void addMissingDynamoError(List<StructureError> errors) {
        try {
            Class<?> errorTypes = Class.forName("gregtech.api.structure.error.ErrorType");
            Class<?> hatchElements = Class.forName("gregtech.api.enums.HatchElement");
            Class<?> structureErrors = Class.forName("gregtech.api.structure.error.StructureErrors");
            Object tooFew = errorTypes.getField("TOO_FEW")
                .get(null);
            Object dynamo = hatchElements.getField("Dynamo")
                .get(null);
            Class<?>[] signature = { errorTypes, hatchElements, int.class, int.class };
            Method hatchCount = structureErrors.getMethod("hatchCount", signature);
            Object error = hatchCount.invoke(null, tooFew, dynamo, 0, 1);
            @SuppressWarnings("unchecked")
            List<StructureError> list = errors;
            list.add((StructureError) error);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Structure hints are cosmetic; never abort machine construction if the error API is unavailable.
        }
    }
}
