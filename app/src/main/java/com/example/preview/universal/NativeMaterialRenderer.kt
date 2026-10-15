package com.example.preview.universal

/**
 * NativeMaterialRenderer
 * 
 * Provides authentic Android Material 3 and Flutter pixel-perfect styles,
 * interactive ripple effects, elevation shadows, typography scales,
 * and 3D spatial transformations matching installed native APKs.
 */
object NativeMaterialRenderer {

    fun generateMaterial3Css(theme: PreviewTheme): String {
        return """
        /* Material 3 Dynamic Design Tokens */
        :root {
            --md-sys-color-primary: ${theme.primaryColor};
            --md-sys-color-on-primary: ${theme.onPrimaryColor};
            --md-sys-color-primary-container: ${theme.primaryContainer};
            --md-sys-color-on-primary-container: ${if (theme.isDark) "#EADDFF" else "#21005D"};
            --md-sys-color-secondary: ${theme.secondaryColor};
            --md-sys-color-secondary-container: ${if (theme.isDark) "#4A4458" else "#E8DEF8"};
            --md-sys-color-surface: ${theme.surfaceColor};
            --md-sys-color-surface-variant: ${if (theme.isDark) "#49454F" else "#E7E0EC"};
            --md-sys-color-background: ${theme.backgroundColor};
            --md-sys-color-on-background: ${theme.onSurfaceColor};
            --md-sys-color-on-surface: ${theme.onSurfaceColor};
            --md-sys-color-on-surface-variant: ${if (theme.isDark) "#CAC4D0" else "#49454F"};
            --md-sys-color-outline: ${if (theme.isDark) "#938F99" else "#79747E"};
            --md-sys-color-outline-variant: ${if (theme.isDark) "#49454F" else "#CAC4D0"};
            --md-sys-color-error: #B3261E;
            --md-sys-color-on-error: #FFFFFF;
            --md-sys-elevation-level1: 0px 1px 3px 1px rgba(0, 0, 0, 0.15), 0px 1px 2px 0px rgba(0, 0, 0, 0.30);
            --md-sys-elevation-level2: 0px 2px 6px 2px rgba(0, 0, 0, 0.15), 0px 1px 2px 0px rgba(0, 0, 0, 0.30);
            --md-sys-elevation-level3: 0px 4px 8px 3px rgba(0, 0, 0, 0.15), 0px 1px 3px 0px rgba(0, 0, 0, 0.30);
            --md-sys-elevation-level4: 0px 6px 10px 4px rgba(0, 0, 0, 0.15), 0px 2px 3px 0px rgba(0, 0, 0, 0.30);
            --md-sys-elevation-level5: 0px 8px 12px 6px rgba(0, 0, 0, 0.15), 0px 4px 4px 0px rgba(0, 0, 0, 0.30);
            --md-sys-shape-corner-none: 0px;
            --md-sys-shape-corner-extra-small: 4px;
            --md-sys-shape-corner-small: 8px;
            --md-sys-shape-corner-medium: 12px;
            --md-sys-shape-corner-large: 16px;
            --md-sys-shape-corner-extra-large: 28px;
            --md-sys-shape-corner-full: 9999px;
        }

        /* Material 3 Elevation & Shadow Maps */
        .elevation-0 { box-shadow: none; }
        .elevation-1 { box-shadow: var(--md-sys-elevation-level1); }
        .elevation-2 { box-shadow: var(--md-sys-elevation-level2); }
        .elevation-3 { box-shadow: var(--md-sys-elevation-level3); }
        .elevation-4 { box-shadow: var(--md-sys-elevation-level4); }
        .elevation-5 { box-shadow: var(--md-sys-elevation-level5); }

        /* Material 3 Interactive Ripple Wave */
        .m3-ripple {
            position: relative;
            overflow: hidden;
            user-select: none;
            cursor: pointer;
            transition: transform 0.15s cubic-bezier(0.2, 0, 0, 1), box-shadow 0.2s cubic-bezier(0.2, 0, 0, 1);
        }
        .m3-ripple:active {
            transform: scale(0.97);
        }
        .m3-ripple::after {
            content: '';
            position: absolute;
            top: 50%;
            left: 50%;
            width: 100%;
            height: 100%;
            background: rgba(255, 255, 255, 0.2);
            opacity: 0;
            border-radius: inherit;
            transform: translate(-50%, -50%) scale(0);
            transition: transform 0.4s ease-out, opacity 0.3s ease-out;
            pointer-events: none;
        }
        .m3-ripple:active::after {
            transform: translate(-50%, -50%) scale(2.2);
            opacity: 1;
            transition: 0s;
        }

        /* Material 3 Buttons */
        .m3-btn-filled {
            background-color: var(--md-sys-color-primary);
            color: var(--md-sys-color-on-primary);
            border: none;
            border-radius: var(--md-sys-shape-corner-full);
            padding: 10px 24px;
            font-size: 14px;
            font-weight: 500;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            box-shadow: var(--md-sys-elevation-level1);
        }
        .m3-btn-elevated {
            background-color: var(--md-sys-color-surface);
            color: var(--md-sys-color-primary);
            border: none;
            border-radius: var(--md-sys-shape-corner-full);
            padding: 10px 24px;
            font-size: 14px;
            font-weight: 500;
            box-shadow: var(--md-sys-elevation-level1);
        }
        .m3-btn-tonal {
            background-color: var(--md-sys-color-secondary-container);
            color: var(--md-sys-color-on-surface);
            border: none;
            border-radius: var(--md-sys-shape-corner-full);
            padding: 10px 24px;
            font-size: 14px;
            font-weight: 500;
        }
        .m3-btn-outlined {
            background-color: transparent;
            color: var(--md-sys-color-primary);
            border: 1px solid var(--md-sys-color-outline);
            border-radius: var(--md-sys-shape-corner-full);
            padding: 10px 24px;
            font-size: 14px;
            font-weight: 500;
        }
        .m3-btn-text {
            background-color: transparent;
            color: var(--md-sys-color-primary);
            border: none;
            border-radius: var(--md-sys-shape-corner-full);
            padding: 8px 12px;
            font-size: 14px;
            font-weight: 500;
        }

        /* Material 3 Cards */
        .m3-card {
            background-color: var(--md-sys-color-surface);
            border-radius: var(--md-sys-shape-corner-large);
            box-shadow: var(--md-sys-elevation-level1);
            border: 1px solid var(--md-sys-color-outline-variant);
            transition: box-shadow 0.2s ease, transform 0.15s ease;
        }

        /* Material 3 Outlined Text Field */
        .m3-text-field-box {
            position: relative;
            width: 100%;
            margin: 6px 0;
        }
        .m3-text-input {
            width: 100%;
            padding: 14px 16px;
            border-radius: var(--md-sys-shape-corner-small);
            border: 1.5px solid var(--md-sys-color-outline);
            background: transparent;
            color: var(--md-sys-color-on-surface);
            font-size: 15px;
            outline: none;
            transition: border-color 0.2s ease, box-shadow 0.2s ease;
        }
        .m3-text-input:focus {
            border-color: var(--md-sys-color-primary);
            box-shadow: 0 0 0 1px var(--md-sys-color-primary);
        }

        /* 3D Scene Container & Isometric Presets */
        .scene-3d-viewport {
            perspective: 1000px;
            perspective-origin: center;
            transform-style: preserve-3d;
        }
        .scene-3d-element {
            transition: transform 0.3s cubic-bezier(0.2, 0, 0, 1);
            transform-style: preserve-3d;
        }

        /* Smooth Physics Scrolling */
        .smooth-scroll-container {
            overflow-y: auto;
            overflow-x: hidden;
            -webkit-overflow-scrolling: touch;
            overscroll-behavior-y: contain;
            scrollbar-width: thin;
            scrollbar-color: var(--md-sys-color-outline-variant) transparent;
        }
        .smooth-scroll-container::-webkit-scrollbar {
            width: 4px;
        }
        .smooth-scroll-container::-webkit-scrollbar-thumb {
            background: var(--md-sys-color-outline-variant);
            border-radius: 4px;
        }
        """
    }
}
