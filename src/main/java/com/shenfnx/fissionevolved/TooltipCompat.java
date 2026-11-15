package com.shenfnx.fissionevolved;

import java.lang.reflect.Method;

import gregtech.api.structure.IStructureChannels;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * Bridges the two incompatible generations of GT5U's {@link MultiblockTooltipBuilder}.
 *
 * <p>
 * Fission Evolved 0.1.0 was compiled against GT5U 5.09.54.98 (GTNH 2.9.0-beta-2 / daily), where the tooltip
 * builder was rewritten: {@code addCasing(String,String,boolean)}, {@code addSubChannel(IStructureChannels)}
 * and the three-argument hatch helpers ({@code addInputBus(String,String,int...)}, ...) were introduced, while
 * the older {@code addCasingInfoMin} / {@code addSubChannelUsage} helpers were removed. GTNH 2.9.0 beta-1 ships
 * GT5U 5.09.52.604, which has exactly the opposite set. A direct call therefore throws
 * {@link NoSuchMethodError} on whichever generation it was not compiled against.
 *
 * <p>
 * Every version-sensitive call is resolved reflectively here, so a single jar renders a useful tooltip on both
 * generations. Each helper takes the builder as its first argument and returns it, which keeps the compiled
 * call-site stack shape identical to an {@code invokevirtual} (receiver + args) after switching to
 * {@code invokestatic} — this is what makes the binary patch possible without editing method bodies.
 * </p>
 */
final class TooltipCompat {

    private TooltipCompat() {}

    private static boolean invoke(MultiblockTooltipBuilder builder, String name, Class<?>[] signature,
        Object[] args) {
        try {
            Method method = MultiblockTooltipBuilder.class.getMethod(name, signature);
            method.invoke(builder, args);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    static MultiblockTooltipBuilder addCasing(MultiblockTooltipBuilder builder, String count, String name,
        boolean required) {
        if (!invoke(
            builder,
            "addCasing",
            new Class<?>[] { String.class, String.class, boolean.class },
            new Object[] { count, name, required })) {
            if (!invoke(
                builder,
                "addCasingInfoMin",
                new Class<?>[] { String.class, int.class, boolean.class },
                new Object[] { name, parseCount(count), required })) {
                invoke(
                    builder,
                    "addOtherStructurePart",
                    new Class<?>[] { String.class, String.class },
                    new Object[] { count, name });
            }
        }
        return builder;
    }

    private static MultiblockTooltipBuilder addHatch(MultiblockTooltipBuilder builder, String method, String count,
        String name, int[] dots) {
        if (!invoke(
            builder,
            method,
            new Class<?>[] { String.class, String.class, int[].class },
            new Object[] { count, name, dots })) {
            if (!invoke(
                builder,
                method,
                new Class<?>[] { String.class, int[].class },
                new Object[] { name, dots })) {
                invoke(builder, method, new Class<?>[] { String.class }, new Object[] { name });
            }
        }
        return builder;
    }

    static MultiblockTooltipBuilder addInputBus(MultiblockTooltipBuilder b, String c, String n, int[] d) {
        return addHatch(b, "addInputBus", c, n, d);
    }

    static MultiblockTooltipBuilder addOutputBus(MultiblockTooltipBuilder b, String c, String n, int[] d) {
        return addHatch(b, "addOutputBus", c, n, d);
    }

    static MultiblockTooltipBuilder addInputHatch(MultiblockTooltipBuilder b, String c, String n, int[] d) {
        return addHatch(b, "addInputHatch", c, n, d);
    }

    static MultiblockTooltipBuilder addOutputHatch(MultiblockTooltipBuilder b, String c, String n, int[] d) {
        return addHatch(b, "addOutputHatch", c, n, d);
    }

    static MultiblockTooltipBuilder addDynamoHatch(MultiblockTooltipBuilder b, String c, String n, int[] d) {
        return addHatch(b, "addDynamoHatch", c, n, d);
    }

    static MultiblockTooltipBuilder addMaintenanceHatch(MultiblockTooltipBuilder b, String c, String n, int[] d) {
        return addHatch(b, "addMaintenanceHatch", c, n, d);
    }

    static MultiblockTooltipBuilder addSubChannel(MultiblockTooltipBuilder builder, IStructureChannels channel) {
        if (!invoke(
            builder,
            "addSubChannel",
            new Class<?>[] { IStructureChannels.class },
            new Object[] { channel })) {
            invoke(
                builder,
                "addSubChannelUsage",
                new Class<?>[] { IStructureChannels.class },
                new Object[] { channel });
        }
        return builder;
    }

    /**
     * beta-1 keeps {@code toolTipFinisher(EnumChatFormatting,int,String...)}; newer dailies only expose
     * {@code toolTipFinisher(String...)}. The colour is {@link Object} so this class never links to a Minecraft
     * class, which keeps it compilable without the deobfuscated Minecraft dependency.
     */
    static MultiblockTooltipBuilder finish(MultiblockTooltipBuilder builder, Object color, int line, String text) {
        return finish(builder, color, line, new String[] { text });
    }

    static MultiblockTooltipBuilder finish(MultiblockTooltipBuilder builder, Object color, int line, String[] text) {
        try {
            for (Method method : MultiblockTooltipBuilder.class.getMethods()) {
                if ("toolTipFinisher".equals(method.getName()) && method.getParameterTypes().length == 3
                    && method.getParameterTypes()[1] == int.class
                    && method.getParameterTypes()[2] == String[].class) {
                    method.invoke(builder, color, line, text);
                    return builder;
                }
            }
            for (Method method : MultiblockTooltipBuilder.class.getMethods()) {
                if ("toolTipFinisher".equals(method.getName()) && method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0] == String[].class) {
                    method.invoke(builder, (Object) text);
                    return builder;
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Tooltip finishing is cosmetic; never abort machine construction because of it.
        }
        return builder;
    }

    private static int parseCount(String count) {
        int value = 0;
        for (int i = 0; i < count.length(); i++) {
            char c = count.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            value = value * 10 + (c - '0');
        }
        return value;
    }
}
