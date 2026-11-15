package com.shenfnx.fissionevolved;

import java.lang.reflect.Method;

import gregtech.api.structure.IStructureChannels;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * Bridges the two incompatible generations of GT5U's {@link MultiblockTooltipBuilder}.
 *
 * <p>
 * Fission Evolved 0.1.0 was compiled against GT5U 5.09.54.98 (GTNH 2.9.0-beta-2 / daily), where the tooltip
 * builder was rewritten: {@code addCasing(String,String,boolean)}, {@code addSubChannel(IStructureChannels)} and
 * the three-argument hatch helpers ({@code addInputBus(String,String,int...)}, ...) were introduced, while the
 * older {@code addCasingInfoMin} / {@code addSubChannelUsage} helpers were removed. GTNH 2.9.0 beta-1 ships GT5U
 * 5.09.52.604, which has exactly the opposite set. A direct call therefore throws
 * {@link NoSuchMethodError} on whichever generation it was not compiled against.
 *
 * <p>
 * Every version-sensitive call is resolved reflectively here, so a single jar renders a useful tooltip on both
 * generations. Each helper takes the builder as its first argument and returns it, which keeps the compiled
 * call-site stack shape identical to an {@code invokevirtual} (receiver + args) after switching to
 * {@code invokestatic}. Tooltip content is purely informational, so a missing overload is skipped instead of
 * aborting machine construction.
 * </p>
 */
final class TooltipCompat {

    private static final Class<?>[] CASING_NEW = { String.class, String.class, boolean.class };
    private static final Class<?>[] CASING_OLD = { String.class, int.class, boolean.class };
    private static final Class<?>[] PART = { String.class, String.class };
    private static final Class<?>[] HATCH_NEW = { String.class, String.class, int[].class };
    private static final Class<?>[] HATCH_OLD = { String.class, int[].class };
    private static final Class<?>[] HATCH_MIN = { String.class };
    private static final Class<?>[] CHANNEL = { IStructureChannels.class };

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
        Object[] args = { count, name, required };
        if (invoke(builder, "addCasing", CASING_NEW, args)) {
            return builder;
        }
        Object[] legacy = { name, parseCount(count), required };
        if (invoke(builder, "addCasingInfoMin", CASING_OLD, legacy)) {
            return builder;
        }
        Object[] part = { count, name };
        invoke(builder, "addOtherStructurePart", PART, part);
        return builder;
    }

    private static MultiblockTooltipBuilder addHatch(MultiblockTooltipBuilder builder, String method, String count,
        String name, int[] dots) {
        Object[] args = { count, name, dots };
        if (invoke(builder, method, HATCH_NEW, args)) {
            return builder;
        }
        Object[] legacy = { name, dots };
        if (invoke(builder, method, HATCH_OLD, legacy)) {
            return builder;
        }
        Object[] minimal = { name };
        invoke(builder, method, HATCH_MIN, minimal);
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
        Object[] args = { channel };
        if (invoke(builder, "addSubChannel", CHANNEL, args)) {
            return builder;
        }
        invoke(builder, "addSubChannelUsage", CHANNEL, args);
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
        Method legacy = findFinisher(3, String[].class);
        if (legacy != null) {
            try {
                legacy.invoke(builder, color, line, text);
                return builder;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                // fall through to the newer single-argument form
            }
        }
        Method modern = findFinisher(1, String[].class);
        if (modern != null) {
            try {
                modern.invoke(builder, (Object) text);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                // Tooltip finishing is cosmetic; never abort machine construction because of it.
            }
        }
        return builder;
    }

    private static Method findFinisher(int arity, Class<?> last) {
        for (Method method : MultiblockTooltipBuilder.class.getMethods()) {
            if (!"toolTipFinisher".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == arity && params[arity - 1] == last) {
                return method;
            }
        }
        return null;
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
