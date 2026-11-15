package com.shenfnx.fissionevolved;

import java.lang.reflect.Method;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.render.TextureFactory;

/**
 * Provides the animated controller front texture on both GT5U generations.
 *
 * <p>
 * GT5U 5.09.54.98 (GTNH 2.9.0-beta-2) added
 * {@code Textures.BlockIcons.createTextureWithCasing(ICasingTextureProvider, ForgeDirection, ForgeDirection,
 * boolean, IIconContainer, IIconContainer, IIconContainer, IIconContainer)}. GTNH 2.9.0 beta-1 (5.09.52.604)
 * does not have it. On beta-1 we fall back to the classic {@link TextureFactory} builder, which composes the
 * registered casing texture with a front overlay and is available in every GT5U generation.
 * </p>
 */
final class TextureCompat {

    private TextureCompat() {}

    static boolean isAvailable() {
        return findFactory() != null;
    }

    /**
     * Exact-signature drop-in for GT5U's {@code Textures.BlockIcons.createTextureWithCasing(...)}, used by the
     * binary patch so no call-site stack shape changes.
     */
    static ITexture[] createTextureWithCasing(ICasingTextureProvider provider, ForgeDirection side,
        ForgeDirection facing, boolean active, IIconContainer frontInactive, IIconContainer frontInactiveGlow,
        IIconContainer frontActive, IIconContainer frontActiveGlow) {
        return createFront(
            provider,
            side,
            facing,
            active,
            provider.getCasingTexture(),
            frontInactive,
            frontInactiveGlow,
            frontActive,
            frontActiveGlow);
    }

    /** Returns null when neither generation supports the animated front, so callers can use their static fallback. */
    static ITexture[] createFront(ICasingTextureProvider provider, ForgeDirection side, ForgeDirection facing,
        boolean active, ITexture casingTexture, IIconContainer frontInactive, IIconContainer frontInactiveGlow,
        IIconContainer frontActive, IIconContainer frontActiveGlow) {
        Method factory = findFactory();
        if (factory != null) {
            try {
                return (ITexture[]) factory
                    .invoke(null, provider, side, facing, active, frontInactive, frontInactiveGlow, frontActive,
                        frontActiveGlow);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                // fall through to the portable builder
            }
        }
        return buildPortable(side, facing, active, casingTexture, frontInactive, frontInactiveGlow, frontActive,
            frontActiveGlow);
    }

    private static Method findFactory() {
        try {
            return Class.forName("gregtech.api.enums.Textures$BlockIcons")
                .getMethod(
                    "createTextureWithCasing",
                    ICasingTextureProvider.class,
                    ForgeDirection.class,
                    ForgeDirection.class,
                    boolean.class,
                    IIconContainer.class,
                    IIconContainer.class,
                    IIconContainer.class,
                    IIconContainer.class);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            return null;
        }
    }

    private static ITexture[] buildPortable(ForgeDirection side, ForgeDirection facing, boolean active,
        ITexture casingTexture, IIconContainer frontInactive, IIconContainer frontInactiveGlow,
        IIconContainer frontActive, IIconContainer frontActiveGlow) {
        ITexture[] result = new ITexture[] { casingTexture };
        if (side != facing) {
            return result;
        }
        ITexture front = TextureFactory.builder()
            .addIcon(active ? frontActive : frontInactive)
            .extFacing()
            .build();
        ITexture glow = TextureFactory.builder()
            .addIcon(active ? frontActiveGlow : frontInactiveGlow)
            .extFacing()
            .build();
        return new ITexture[] { casingTexture, front, glow };
    }
}
